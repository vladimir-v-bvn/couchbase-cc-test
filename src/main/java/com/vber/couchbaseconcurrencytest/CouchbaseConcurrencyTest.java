package com.vber.couchbaseconcurrencytest;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 *
 * @author vber
 * <p>
 * A multithreading Couchbase performance test where nothing is blocked.
 * <p>
 * Set up:<br>
 * readProperties() - reads properties<br>
 * readJsonObjectFromFile() - reads JSON object from file <br>
 * connectToCouchbase() - connects to Couchbase <br>
 * <p>
 * The test: <br>
 * The number of threads is set as command line argument (currently commented) or in the main method. <br>
 * runTest() method is implemented like while() cycle controlled by isKeepRunning boolean variable. <br>
 * ExecutorService is started as Executors.newFixedThreadPool(numberOfTreads) <br>
 * and runnable task runTest() is submitted numberOfTreads times. <br>
 * At the same time SingleThreadScheduledExecutor is started to stop ExecutorService <br>
 * after 3 minutes and to set isKeepRunning to false to stop runTest(). <br>
 * As the last step collected performance data are printed. <br>
 * <p>
 * Non-blocking multithreading implementation: <br>
 * Performance data are collected to non-blocking LongAdder variables. <br>
 * startTime and finishTime of tests are written as they are, <br>
 * test time i.e. (finishTime - startTime) is calculated after the tests as LongAdder.sum(), <br>
 * startTime is written as (-1)*startTime. <br>
 * Generated random UUID is used as a key of Couchbase documents. <br>
 * To prevent modification of UUID by other threads during the test <br>
 * UUID is made ThreadLocal, which is non-blocking as well. <br>
 */
public class CouchbaseConcurrencyTest {

  static String connectionString;
  static String userName;
  static char[] password;
  static String bucketName;

  private static Cluster cluster = null;
  private static Bucket bucket = null;

  private static int numberOfTreads;
  private static int testTimeSeconds = 60*3;
  private static boolean isKeepRunning = true;
  
  private static String jsonFile;
  private static JSONObject jsonObject;

  private static LongAdder perfomanceResultCounter = new LongAdder();
  private static LongAdder perfomanceResultWriteTime = new LongAdder();
  private static LongAdder perfomanceResultRead3Time = new LongAdder();
  
  private static Logger logger = LogManager.getLogger(CouchbaseConcurrencyTest.class);
  
  /**
   * 
   * @param args
   * 
   * args[0] is numberOfTreads (currently commented)
   * <p>
   * The method<br>
   *            (1) sets up the environment<br>
   *            (2) starts the main routine startExecutorService()<br>
   *            (3) prints performance test results.<br>
   */
  public static void main(String[] args) {

    readProperties();
    readJsonObjectFromFile();
    connectToCouchbase();
    
    numberOfTreads = 4;
  //numberOfTreads = Integer.parseInt(args[0]) ;
    Runnable task = () -> runTest();
    startExecutorService(task, numberOfTreads);
    
    printPerformanceTestResults();
    
  }

  /**
   * 
   * @param 
   * <b>Runnable task</b> is runTest() method <br>
   * <b>int numberOfTreads</b> is number of treads used in the test <br>
   * <p>
   * ExecutorService is started as Executors.newFixedThreadPool(numberOfTreads) <br>
   * and runnable task runTest() is submited numberOfTreads times.<br>
   * At the same time SingleThreadScheduledExecutor is started <br>
   * to stop ExecutorService after 3 minutes, <br>
   * to set isKeepRunning to false to stop runTest() <br>
   * and to disconnect from Couchbase.
   */
  private static void startExecutorService(Runnable task, int numberOfTreads) {

    ExecutorService service = Executors.newFixedThreadPool(numberOfTreads);
    for (int i = 0; i < numberOfTreads; i++) {
      service.submit(task);
    }

    ScheduledExecutorService scheduledService = Executors.newSingleThreadScheduledExecutor();
    scheduledService.schedule(new Runnable(){
      public void run(){
        isKeepRunning = false;
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          logger.error("scheduledService thread can't sleep.");
          e.printStackTrace();
        }
        service.shutdown();
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          logger.error("scheduledService thread can't sleep.");
          e.printStackTrace();
        }
        try {
          cluster.disconnect();
        } catch (Exception e) {
          logger.error("Couchbase cluster can't disconnect.");
          e.printStackTrace();
        } finally {
          cluster.disconnect();
        }
        try {
          if (!service.awaitTermination(1, TimeUnit.SECONDS)) {
            service.shutdownNow();
            if (!service.awaitTermination(1, TimeUnit.SECONDS))
              logger.error("newFixedThreadPool Service not terminated.");
              Thread.currentThread().interrupt(); //scheduledService interrupted
            }
        } catch (InterruptedException e) {
          System.exit(1);
        }
      }
    }, testTimeSeconds, TimeUnit.SECONDS);

  }
  
  /**
   * runTest() method is the test itself. <br>
   * It is implemented like while() cycle controlled by isKeepRunning boolean variable.
   */
  private static void runTest() {

    ThreadLocal<String> uuid = new ThreadLocal<>();
    while(isKeepRunning) {
      uuid.set(UUID.randomUUID().toString()); 
      perfomanceResultCounter.increment();
      //write test
      perfomanceResultWriteTime.add((-1) * System.currentTimeMillis());
        bucket.defaultCollection().upsert(uuid.get(), jsonObject);
      perfomanceResultWriteTime.add(System.currentTimeMillis());
      //read*3 test
      perfomanceResultRead3Time.add((-1) * System.currentTimeMillis());
        bucket.defaultCollection().get(uuid.get());
        bucket.defaultCollection().get(uuid.get());
        bucket.defaultCollection().get(uuid.get());
      perfomanceResultRead3Time.add(System.currentTimeMillis());
    }

  }

  /**
   * private readProperties() is a self-documented method, it performs exactly according his name. 
   */
  private static void readProperties() {
    try (InputStream inputStream = new FileInputStream("config.properties")) {
      Properties prop = new Properties();
      prop.load(inputStream);
      connectionString = prop.getProperty("connectionString");
      userName = prop.getProperty("userName");
      password = prop.getProperty("password").toCharArray();
      bucketName = prop.getProperty("bucketName");
      numberOfTreads = Integer.parseInt(prop.getProperty("numberOfTreads"));
      jsonFile = prop.getProperty("jsonFile");
    } catch (IOException e) {
      logger.error("can't read properties");
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * private readJsonObjectFromFile() is a self-documented method, it performs exactly according his name. 
   */
  private static void readJsonObjectFromFile() {
    JSONParser jsonParser = new JSONParser();
    try (FileReader reader = new FileReader(jsonFile)) {
      jsonObject = (JSONObject)jsonParser.parse(reader);
    } catch (Exception e) {
      logger.error("Can't read or parse JSON file");
      e.printStackTrace();
      System.exit(1);
    }
  }
  
  /**
   * private connectToCouchbase() is a self-documented method, it performs exactly according his name. 
   */
  private static void connectToCouchbase() {
    try {
      cluster = Cluster.connect(connectionString, userName, String.valueOf(password));
      Arrays.fill(password, '*');
      password = null;
      bucket = cluster.bucket(bucketName);
      bucket.waitUntilReady(Duration.parse("PT10S"));
    } catch (Exception e) {
      logger.error("can't connect to Couchbase");
      e.printStackTrace();
      System.exit(1);
    }
  }  

  /**
   * printPerformanceTestResults() waits (test time) in seconds to finish the test <br>
   * plus additional 3 seconds to close pools and disconnect from Couchbase, <br>
   * then calculates counters accumulated in LongAdder variables <br>
   * and then prints results of the test. 
   */
  private static void printPerformanceTestResults() {
    
    try {
      Thread.sleep(testTimeSeconds*1000 + 3*1000);
    } catch (InterruptedException e) {
      logger.error("Main thread can't sleep.");
      e.printStackTrace();
      System.exit(1);
    }
    
    int perfomanceResultCounterInt = (int)perfomanceResultCounter.sum();
    long perfomanceResultWriteTimeLong = perfomanceResultWriteTime.sum();
    long perfomanceResultRead3TimeLong = perfomanceResultRead3Time.sum();

    System.out.printf("%20s", "numberOfTreads");
    System.out.printf("%20s", "count");
    System.out.printf("%20s", "timeWrites");
    System.out.printf("%20s", "averageTimeWrites");
    System.out.printf("%20s", "timeReadsX3");
    System.out.printf("%20s", "averageTimeReadsX3");
    System.out.printf("%n");
    System.out.printf("%20s", numberOfTreads);
    System.out.printf("%20s", perfomanceResultCounterInt);
    System.out.printf("%20s", perfomanceResultWriteTimeLong);
    System.out.printf("%20s", perfomanceResultCounterInt != 0 ? perfomanceResultWriteTimeLong/perfomanceResultCounterInt : "-");
    System.out.printf("%20s", perfomanceResultRead3TimeLong);
    System.out.printf("%20s", perfomanceResultCounterInt != 0 ? perfomanceResultRead3TimeLong/perfomanceResultCounterInt : "-");
    System.out.printf("%n");

    System.exit(0); //it's needed for Eclipse only

  }

}
