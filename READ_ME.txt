In broad strokes, this project entails a java module and a SuperCollider module.  The java module has two dependencies: javaOSC and OpenIMAJ.  

The Java Module
Some of the dependency code is only compatible with old versions of Java.  Java 1.8 works for everything.  To run in VS Code, go to project settings.  Ensure the OpenIMAJ jar is in the class path (should be there automatically, based on settings.json). In the compiler tab, uncheck the use --release option and choose 1.8 for both source and target compatability.  This is enough to make everything work in VSCode Version: 1.98.1 (Universal)
Commit: 2fc07b811f760549dab9be9d2bedd06c51dfcb9a
Date: 2025-03-10T15:38:08.854Z
Electron: 34.2.0
ElectronBuildId: 11160463
Chromium: 132.0.6834.196
Node.js: 20.18.2
V8: 13.2.152.36-electron.0
OS: Darwin x64 24.0.0

To run, go to Installation.java and run (use this main method).
