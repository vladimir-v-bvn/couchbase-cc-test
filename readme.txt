A multithreading Couchbase performance test where nothing is blocked.

The project is compiled with Java 17.

Set up:
  readProperties() - reads properties
  readJsonObjectFromFile() - reads JSON object from file
  connectToCouchbase() - connects to Couchbase

The test:
The number of threads is set as command line argument (currently commented) or in the main method.
Runnable task is created from the runTest() method.
runTest() method is implemented like while() cycle controlled by isKeepRunning boolean variable.
ExecutorService is started as Executors.newFixedThreadPool(numberOfTreads)
and runnable task runTest() is submited numberOfTreads times.
At the same time SingleThreadScheduledExecutor started to stop ExecutorService
after 3 minutes and to set isKeepRunning to false to stop runTest(). 
As the last step collected performance data are printed.

Non-blocking multithreading implementation:
Performance data are collected to non-blocking LongAdder variables.
startTime and finishTime of tests are written as they are,
test time i.e. (finishTime - startTime) is calculated after the tests as LongAdder.sum(),
startTime is written as (-1)*startTime.
Generated random UUID is used as a key of Couchbase documents.
To prevent modification of UUID by other threads during the test
UUID is made ThreadLocal, which is non-blocking as well. 

Results of the test:
The test was run on my Core i5 notebook with 4 cores. No warm up procedures were done.
Some databases, messaging servers and so on are running on the notebook in the background.
Couchbase runs on the same notebook as well.
So, the results of the test are more or less irrelevant.
Nevertheless test gives a clear picture:
from 1 to 4 or even 8 threads number of fulfilled tasks grows
(more threads submit more tasks,
submitted tasks are fulfilled after the 3 seconds are over - 
because the pool is closed with shutdown(), not with shutdownNow())
and average time to process one task grows as well because of more load.
In the long running tests (some minutes) with 4 threads average time to process one task
becomes stable and quite low.
After 8 treads degradation of performance starts.
No issues with the multithreading Couchbase performance itself were noticed.

Conclusion:
No issues with the multithreading Couchbase performance itself were noticed.
The multithreading Couchbase performance must be tested in a real production environment.