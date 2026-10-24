# CutCleanReborn — Paper 1.21.11

Standalone Paper 1.21.11 project, using Java 21. Sources, configuration, logo and tests are local to this directory. API adaptations and validation history are recorded in [the workspace status](https://github.com/Zazac1/Cut-Clean-Reborn/blob/main/validation/porting/README.md).

```powershell
.\gradlew.bat build
.\gradlew.bat smokeTest
```

Install `build/libs/cutcleanreborn-0.1.0+1.21.11.jar`. Native tests use isolated worlds under `build/smoke-server/`, preserving existing worlds and configuration. Compilation and simulated interactions do not replace actual player checks; consult the current workspace reports for matching evidence.
