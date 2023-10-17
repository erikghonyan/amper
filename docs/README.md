# Deft 

Deft is a tool to help configure projects for the purpose of building, packaging, publishing, and more. At its current stage however, the focus is primarily on configuring projects for the purpose of building. It is still very much experimental, and our main goal for opening it up is to validate the ideas and get your feedback.

Deft currently supports Kotlin and Java applications, targeting JVM, Android, iOS, macOS, and Linux platforms. In the future it could also support other languages directly. 

Deft is currently using Gradle as the backend and YAML as the frontend, i.e. the project configuration. Custom tasks, publishing of libraries to Maven, CocoaPods, and packaging desktop apps are all provided via Gradle interop for now.

Supported features:
* Creating and running JVM, Android, iOS, Linux and macOS applications.
* Creating Kotlin Multiplatform libraries.
* Running tests.
* Mixing Kotlin, Java and Swift code.
* Using [Compose Multiplatform](Documentation.md#configuring-compose-multiplatform).
* [Multi-module](Documentation.md#internal-dependencies) projects.
* [Gradle interop](Documentation.md#gradle-interop) (including combining Deft and Gradle modules in one projects).
* Code assistance for [module manifest files](Documentation.md#module-manifest-file-anatomy) in IntelliJ IDEA and Fleet.

Planned features:
* More [product type](Documentation.md#product-types) and platforms, such as watchOS, Windows, etc.
* [Platform-specific test types](Documentation.md#special-types-of-tests), including android instrumented tests.
* [Native dependencies](Documentation.md#native-dependencies) support, such as CocoaPods, Swift Package Manager.
* Integration with [Gradle version catalogs](Documentation.md#dependencyversion-catalogs).
* [Packaging](Documentation.md#packaging) and [publication](Documentation.md#publishing). 
* [Build variants](Documentation.md#build-variants) support.
* [Extensibility](Documentation.md#extensibility).
* Workspaces (organizing multiple modules together).
* Support more Kotlin and Kotlin Multiplatform scenarios and configurations out-of-the-box.


Check the [FAQ for more question](FAQ.md) and the list of [known issues](KnownIssues.md).

Join the Slack channel<!LINK!> for discussions and share you feedback and ideas in the [tracker](https://youtrack.jetbrains.com/issues/DEFT).  

For a quick start:
* Check the [usage instructions](Setup.md)
* [Tutorial](Tutorial.md)  
* [Documentation](Documentation.md) 
* [Example projects](../examples)
* Gradle [migration guide](GradleMigration.md)  


## Examples

### Basics
Here is a very basics JVM "Hello, World!" project:
```
|-src
|  |-main.kt
|-test
|  |-MyTest.kt
|-module.yaml
|-...
```

Nothing unexpected in the `main.kt` and `MyTest.kt` files, the interesting part is the `module.yaml`, the Deft manifest file.
In its simplest form it looks like this:
```yaml
# Produce a JVM application 
product: jvm/app
```

That's it. The Kotlin and Java toolchains, test framework and other necessary functionality is configured and available straight out of the box.
You can build it, run it, write test etc.  Check out the [full example](../examples/jvm-with-tests).

### Multiplatform

Let's look at a Compose Multiplatform project with Android, iOS and Desktop JVM apps. Here is the project layout:
```
|-ios-app                  # an iOS application
|  |-src
|  |  |-iosApp.swift       # native Swift code
|  |  |-ViewController.kt
|  |  |-...
|  |-module.yaml
|-android-app              # an Android application
|  |-...
|-jvm-app                  # a JVM application
|  |-...
|-shared                   # shared library
|  |-src                   # common code for all platforms
|  |  |-MainScreen.kt
|  |-src@ios               # iOS-specific code
|  |  |-...           
|  |-src@android           # Android-specific code
|  |  |-...
|  |-test                  # common tests
|  |  |-MainScreenTest.kt      
|  |  |-...
|  |-test@ios              # iOS-specicis tests
|  |  |- ...               
|  |-...
|  |-module.yaml
|-... 
```
 
Notice how the `src/` folder contains Kotlin and Swift code together. It could also be Kotlin and Java, or course.   
Another highlight is the shared module with the common code in the `src` folder and the platform-specific code `src@ios` and `src@android` folders.
Read more about the project layout [here](Documentation.md#project-layout).

Here is how `ios-app/module.yaml` manifest file looks like:
```yaml
# Produce an iOS application
product: ios/app

# Depend on the shared library module: 
dependencies:
  - ../shared

settings:
  # Enable Compose Multiplatform framework
  compose: enabled
```

This is pretty straightforward: it defines an iOS application with a dependency on a shared more and enables the Compose Multiplatform framework.
A more interesting example is `shared/module.yaml`:

```yaml
# Produce a shared library for JVM, Android and iOS platforms:
product:
  type: lib
  platforms: [jvm, android, iosArm64, iosSimulatorArm64, iosX64]

# Shared Compose dependencies:
dependencies:
  - org.jetbrains.compose.foundation:foundation:1.5.0-rc01: exported
  - org.jetbrains.compose.material3:material3:1.5.0-rc01: exported

# Android-only dependencies  
dependencies@android:
  # integration compose with activities
  - androidx.activity:activity-compose:1.7.2: exported
  - androidx.appcompat:appcompat:1.6.1: exported

# iOS-only dependencies with a dependency on a CocoaPod
#   note, that CocoaPods dependencies are not yet implemented in the prototype     
dependencies@ios:
  - pod: 'Alamofire'
    version: '~> 2.0.1'

settings:
  # Enable Kotlin serialization
  kotlin:
    serialization: json
  
  # Enable Compose Multiplatform framework
  compose: enabled
```

A couple of things are worth mentioning. First, the platform-specific `dependencies:` sections with the `@<platform>`-qualifier. [The platform qualifier](Documentation.md#platform-qualifier) can be used both in the manifest and also in the file layout. The qualifier organizes the code, dependencies and settings for a certain platform.  
Second, the `dependencies:` section support not only Kotlin and Maven dependencies, but also [platform-specific package managers](Documentation.md#native-dependencies), such as CocoaPods, Swift Package Manager and others.

These examples show only a limited set of Deft features, of course. Look at the [tutorial](Tutorial.md), [documentation](Documentation.md) and [example projects](../examples) to get more insight on the Deft design and its functionality.     

### More examples
Check our more real world examples:
* [JVM "Hello, World!"](../examples/jvm-kotlin%2Bjava)
* Compose for [iOS](../examples/compose-ios), [Android](../examples/compose-android) and [desktop](../examples/compose-desktop).
* [Multiplatform](../examples/multiplatform) project with shared code.
* [Gradle interop](../examples/gradle-interop)
* And [others](../examples)


### Using Deft in IntelliJ IDEA

See [the setup instructions](Setup.md) to configure your IDE.

Open a Deft project as usual by pointing at the folder with the main `settings.gradle.kts` file:

To run an application:

* use a 'run' (![](images/ij-run-gutter-icon.png)) gutter icon next to the `product: ` section in a module.yaml file:\
 ![img.png](images/ij-run-product.png)


* use a 'run' (![](images/ij-run-gutter-icon.png)) gutter icon next to the `main()` function:\
  ![](images/ij-run-main.png)


* use [Run/Debug configurations](https://www.jetbrains.com/help/idea/run-debug-configuration.html):\
  ![](images/ij-run-config-jvm.png)\
  ![](images/ij-run-config-android.png)


* launch a Gradle task directly:\
  ![](images/ij-run-gradle-task.png)
  

To run tests use the same 'run' (![](images/ij-run-gutter-icon.png)) gutter icon or Gradle run configuration. Read more on [testing in IntelliJ IDEA](https://www.jetbrains.com/help/idea/work-with-tests-in-gradle.html#run_gradle_test).
![](images/ij-run-tests.png)


### Using Deft in Fleet

See [the setup instructions](Setup.md) to configure your IDE.

Open a Deft project as usual by pointing at the folder with the main settings.gradle.kts file:

To run an application:

* use a 'run' (![](images/fleet-run-gutter-icon.png)) gutter icon next to the `product: ` section in a module.yaml file:\
 ![](images/fleet-run-product.png)


* use a 'run' (![](images/fleet-run-gutter-icon.png)) gutter icon next to the `main()` function:\
  ![](images/fleet-run-main.png)


* use [Run configurations](https://www.jetbrains.com/help/fleet/getting-started-with-kotlin-in-fleet.html#create-rc):\
  ![](images/fleet-run-config.png)\
  ![](images/fleet-run-config-ios.png)


* launch a Gradle task directly:\
  ![](images/fleet-run-gradle-task.png)
   
  
  

To run tests use the same 'run' (![](images/fleet-run-gutter-icon.png)) gutter icon or Gradle run configuration

