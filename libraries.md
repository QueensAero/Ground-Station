## Background
Eclipse keeps track of the locations of external *.jar files and dynamic libraries in a '.classpath' file. A sample '.classpath' file for Windows platforms can be found [here](./lib/windows/.classpath.example).

###### Note:
*The "." in front of the ".classpath" filename makes it a hidden file. By default, most operating systems will not display these files in a standard file browser. Look up instructions specific to your OS for working with hidden files.*

In order to maintain cross-compatibility between Mac and Windows platforms, the Ground-Station project maintains the external dependencies for both operating systems in the [Ground-Station/lib/macos](./lib/macos) and [Ground-Station/lib/windows](./lib/windows) folders respectively. These folders contain all of the necessary .jar files and dynamic library files (.dll, .dylib, .jnilib), as well as the '.classpath' files (saved as ".classpath.example").

When someone is setting up the Ground-Station project in Eclipse for the first time, they must run either the [configure.command](./lib/macos/configure.command) script (on macs) or the [configure.bat](./lib/windows/configure.bat) script (on windows). These scripts simply copy the '.classpath.example' file to the base 'Ground-Station' directory and rename it as '.classpath' (this is the file path that Eclipse looks for when opening a project).

## Adding New Libraries
1. Add the required dependencies (.jar, .dll, etc.) to the appropriate library folder for each platform.
2. In Eclipse, open the ‘Properties -> Java Build Path’ window for the Aero_GroundStation project.
![Alt text](img/BuildPathProperties.png?raw=true "Java Build Path Properties")
3. Click on ‘Add JARs…’. (Note: Do not click on ‘Add External JARs…’ as this will use absolute path references rather than relative paths.)
4. Select the JAR files that you wish to add to the project. This step will automatically add entries for each JAR file to the ‘.classpath’ file.
5. If the newly added JAR relies on external libraries then click on ‘\<JAR of interest> -> Native library location -> Edit…’ as shown below. Click on ‘Workspace…’ and select the folder containing the native libraries. (Note: Again, do not click on ‘External Folder…’ as this will use absolute paths rather than relative paths.
![Alt text](img/NativeLibraryEdit.png?raw=true "Native Library Edit")
6. Compare the update ‘.classpath’ file with the ‘.classpath.example’ file for the appropriate platform and make the appropriate updates in the ‘.classpath.example’ file.
7. Test your changes and commit.
