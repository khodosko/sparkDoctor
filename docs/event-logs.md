# Finding Spark Event Logs

SparkDoctor analyzes Spark event logs, not driver stdout or executor log4j output. Spark event logs are JSON-lines listener-event files used by the Spark History Server to reconstruct the Spark UI after an application finishes.

## Local Spark Or Self-Managed Spark

Enable event logging before the Spark application starts:

```bash
spark-submit \
  --conf spark.eventLog.enabled=true \
  --conf spark.eventLog.dir=file:///tmp/spark-events \
  your-app.jar
```

Then analyze the generated event log file or application directory:

```bash
sparkdoctor analyze /tmp/spark-events/eventlog_v2_local-1234567890 --out ./sparkdoctor-report
```

Spark's event-log directory is controlled by `spark.eventLog.dir`, and rolling event logs may create an application directory instead of one flat file.

Spark 4 can write event logs with a directory layout like this:

```text
/tmp/spark-events/
  eventlog_v2_local-.../
    events_1_local-...
    appstatus_local-...
```

For this layout, SparkDoctor can analyze either the parent event-log directory or the `eventlog_v2_*` application directory. It reads the `events_*` stream files and ignores Spark status/helper files.

Reference: [Apache Spark configuration](https://spark.apache.org/docs/latest/configuration.html).

## Spark History Server Directory

If you already have a Spark History Server, use the same backing log directory configured by `spark.history.fs.logDirectory`. Each application event log in that directory can be copied locally and analyzed:

```bash
sparkdoctor analyze path/to/copied/eventlog-or-application-directory --out ./sparkdoctor-report
```

Reference: [Apache Spark monitoring and instrumentation](https://spark.apache.org/docs/3.5.2/monitoring.html).

## Databricks

For Databricks, configure compute log delivery before the cluster or job compute runs. Databricks can deliver driver logs, worker logs, and event logs to a configured location such as a Unity Catalog volume, S3, or DBFS depending on your workspace setup.

After the run, copy the Spark event log file or event-log directory from the configured log-delivery location to your local machine, then run:

```bash
sparkdoctor analyze path/to/databricks/eventlog-or-directory --out ./sparkdoctor-report
```

Reference: [Databricks compute log delivery](https://docs.databricks.com/aws/en/compute/configure#compute-log-delivery).

## Amazon EMR

On Amazon EMR, Spark event logs are commonly available under HDFS path `/var/log/spark/apps/` on the cluster. You can list them with:

```bash
hdfs dfs -ls -R /var/log/spark/apps/
```

Copy the target event log locally, then run:

```bash
sparkdoctor analyze path/to/emr/eventlog --out ./sparkdoctor-report
```

If your EMR setup writes Spark event logs to S3 through `spark.eventLog.dir`, download the relevant event log or application directory first. SparkDoctor currently expects a local file or local directory path.

Reference: [AWS EMR best practices: retrieving Spark event logs](https://aws.github.io/aws-emr-best-practices/docs/benchmarks/Analyzing/retrieve_event_logs/).

## AWS Glue

AWS Glue can write Spark UI event logs to an S3 path when Spark UI logging is enabled. In Glue job parameters, the relevant settings are:

```text
--enable-spark-ui true
--spark-event-logs-path s3://your-bucket/path/
```

Download the generated event log file or rolling-log directory from S3, then run:

```bash
sparkdoctor analyze path/to/glue/eventlog-or-directory --out ./sparkdoctor-report
```

Reference: [AWS Glue Spark UI event logs](https://docs.aws.amazon.com/glue/latest/dg/monitor-spark-ui-jobs.html).

## Important Notes

- SparkDoctor currently reads local files and local directories. Download cloud object-store logs before analysis.
- Event logs may contain SQL text, table names, storage paths, usernames, hostnames, and cluster metadata. Sanitize logs before sharing them publicly.
- For rolling event logs, point SparkDoctor at the directory containing the event-log parts.
- For compressed logs, keep the original extension when possible. SparkDoctor supports `.gz`, `.zstd`, `.zst`, `.lz4`, and `.snappy`.
