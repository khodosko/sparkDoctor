from pyspark.sql import SparkSession
from pyspark.sql import functions as F


def main():
    spark = (
        SparkSession.builder
        .appName("sparkdoctor_real_fixture")
        .getOrCreate()
    )

    try:
        rows = spark.range(0, 1000, 1, numPartitions=8)
        enriched = rows.withColumn("group_id", F.col("id") % 10)
        result = (
            enriched
            .repartition(4, "group_id")
            .groupBy("group_id")
            .count()
            .orderBy("group_id")
            .collect()
        )
        print("sparkdoctor_real_fixture rows:", len(result))
    finally:
        spark.stop()


if __name__ == "__main__":
    main()
