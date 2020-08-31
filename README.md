
# Description

Application to perform I/O load testing on app deployed to Cloud Foundry with Volume Services (NFS) service


# Configuration


Set an environment variable `SPRING_APPLICATION_JSON` equal a JSON string containing the desired configuration.
Below is the default configuration and values.  You may specify only the values needed to override defaults.

```
{
  "logOutputIntervalSeconds": 10,
  "startupDelay": 3000,
  "read": {
    "threads": 1,
    "filename": "testreadfile",
    "filesize": 10485760,
    "chunkSize": 8192,
    "durationSeconds": 60
  },
  "write": {
    "threads": 1,
    "filename": "testwritefile",
    "filesize": 10485760,
    "chunkSize": 8192,
    "durationSeconds": 60
  }
}
```

# Examples

## Example to perform only read test with 2 threads for 30 seconds.

```
cf set-env nfstest SPRING_APPLICATION_JSON '{"read": { "threads": 2, "durationSeconds": 30 }, "write": { "threads": 0 } }'
```



## Example to set all values

```
cf set-env nfstest SPRING_APPLICATION_JSON '{"logOutputIntervalSeconds": 10, "startupDelay": 3000, "read": { "threads": 1, "filename": "testreadfile", "filesize": 10485760, "chunkSize": 8192, "durationSeconds": 60 }, "write": { "threads": 1, "filename": "testwritefile", "filesize": 10485760, "chunkSize": 8192, "durationSeconds": 60 } }'
```


# TODO:

Change time to use values like "1h"

create only 1 file for all read tests -- its creating 1 per read test now.

Add thread number to log output of "Bytes written per second: 52,428,800" - What about CF instance ID?



