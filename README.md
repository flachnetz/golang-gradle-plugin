[![Build Status](https://travis-ci.org/flachnetz/golang-gradle-plugin.svg?branch=master)](https://travis-ci.org/flachnetz/golang-gradle-plugin)
# golang-gradle-plugin

Simple gradle plugin to compile go sources.
It sets up a custom `GOPATH` and installs dependencies via glide or go get.

## Plugin Usage

### With gradle > 2.1
````
plugins {
  id "de.flachnetz.golang-gradle-plugin" version "0.1.14"
}
```

### All gradle versions

```
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "de.flachnetz:golang-gradle-plugin:0.1.14"
  }
}

apply plugin: "de.flachnetz.golang-gradle-plugin"
```

Tasks
-----
The plugin provides the following gradle tasks:
* dependencies
* format
* test
* build and buildStatic
