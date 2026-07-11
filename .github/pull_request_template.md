## Summary

Describe the change.

## Spark Behavior

What Spark event log behavior, metric, detector, recommendation, or output does this affect?

## Tests

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./gradlew test
```

## Checklist

- [ ] I added or updated unit tests.
- [ ] `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` passes.
- [ ] `./gradlew test` passes.
- [ ] I updated README or docs if behavior, output, or usage changed.
- [ ] I did not weaken, skip, delete, or loosen existing tests.
- [ ] This contribution is ready for maintainer review.
