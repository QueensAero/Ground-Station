## Background
Eclipse keeps track of the locations of external *.jar files and dynamic libraries in a '.classpath' file. A sample '.classpath' file for Windows platforms can be found [here](./lib/windows/.classpath.example).

###### Note:
*The "." in front of the ".classpath" filename makes it a hidden file. By default, most operating systems will not display these files in a standard file browser. Look up instructions specific to your OS for working with hidden files.*

In order to maintain cross-compatibility between Mac and Windows platforms, the Ground-Station project maintains the external dependencies for both operating systems in the [Ground-Station/lib/macos](./lib/macos) and [Ground-Station/lib/windows](./lib/windows) folders respectively. These folders contain all of the necessary *.jar files and dynamic library files (*.dll, *.dylib, *.jnilib), as well as the '.classpath' files (saved as ".classpath.example").

When someone is setting up the Ground-Station project in Eclipse for the first time, they must run either the [configure.command](./lib/macos/configure.command) script (on macs) or the [configure.bat](./lib/windows/configure.bat) script (on windows). These scripts simply copy the '.classpath.example' file to the base 'Ground-Station' directory and rename it as '.classpath' (this is the file path that Eclipse looks for when opening a project).

## Adding New Libraries
