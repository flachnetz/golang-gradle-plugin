# golang-gradle-plugin

Simple gradle plugin to compile go sources.
It  sets up a custom `GOPATH` and installs dependencies via glide or go get.

Using the plugin is as simple as applying it directly from github and setting the desired output name for the compiled binary:

```
apply from: "https://raw.githubusercontent.com/flachnetz/golang-gradle-plugin/master/golang.gradle"

golang {
    binaryName = "my-fancy-service"
}
```

Tasks
-----
The plugin provides the following gradle tasks:
* dependencies
* format
* test
* build and buildStatic
