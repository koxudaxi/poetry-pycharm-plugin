# Development
## Building the plugin
You can build and run the plugin either via the command line or through IntelliJ IDEA:

### Shell on Linux or MacOS 
```bash
$ ./gradlew buildPlugin
```

### Command Prompt on Windows
```
$ gradlew.bat buildPlugin
```

### JetBrains IDE on any platform

[Official documentation](https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/using_dev_kit.html])

## Running the IDE with the built plugin
```bash
$ ./gradlew runIde
```

## Unittest
You should add a unittest for the new code. But, This plugin do complex behavior to modules.

You may feel writing unittest difficult.

We should write a unittest as much as possible.

Unittest is not blocker for PRs.

## License For JetBrains' Code
These files are forked from [IntelliJ IDEA Community Edition](https://github.com/JetBrains/intellij-community)

The files are licensed under the Apache License, Version 2.0.

http://www.apache.org/licenses/LICENSE-2.0
