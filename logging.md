## Background
The logging mechanism used throughout the Ground Station code is provided by the 'java.util.logging.*' package. Every class obtains access to the same static logger through the following line (should be present in every class):

```java
private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
```

The logger uses log levels to determine the importance of message and where they should be redirected. The following is a list of all possible log levels in decreasing severity:
* SEVERE
* WARNING
* INFO
* CONFIG
* FINE
* FINER
* FINEST

All log levels automatically get written to a log file with the following naming scheme:  
*yyyy-MM-dd_HH-mm-ss_groundstation.log*

By default, log records of level 'FINE' or higher will also be printed to the console in the Ground Station GUI. It should be noted that any messages printed to stdout or stderr will also be redirected to the console in the Ground Station GUI.

## What logging level should I use?
First, it is important to note that the LOGGER should always be used as an alternative to print statements. This ensures that there is a record of every message that gets displayed to the user.
The following guidelines should be followed when deciding what logging level to use:

1. The '*FINER*' log level should **only** be used for the status updates received from the plane containing GPS data, altitude, speed, etc. This ensures that this data can easily be extracted from the log file for review.

2. The '*FINE*' log level should be used to record any events to the log that may be relevant for future log review, but that are not important enough to display on the console. 

3. The '*INFO*' log level should be used for any information that should be printed to the console for the user, but does not indicate that an error has ocurred. For instance, the 'INFO' level is used to tell the user when messages are sent to the plane.

4. The '*WARNING*' log level should be used for any error that can be recovered from.

5. The '*SEVERE*' log level indicates that a severe error has ocurred and it is likely not possible to recover.
