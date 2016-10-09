package de.flachnetz.golang

import java.nio.file.*
import java.security.*
import java.time.*
import java.time.format.*
import org.gradle.api.*
import org.gradle.api.tasks.*

class GoPlugin implements Plugin<Project> {
    static File relativeTo(File file, File base) {
        return new File(base.toURI().relativize(file.toURI()).path);
    }

    static boolean isGoFile(Object file) {
        return String.valueOf(file).toLowerCase().endsWith(".go")
    }

    static String guessCanonicalImport(File root) {
        def pattern = ~$/^package\s+main\s*//\s*import\s+"([^"]+)"/$
        for (def file in root.listFiles((FileFilter) { isGoFile(it) })) {
            for (def matcher in file.readLines().collect { it =~ pattern }) {
                if (matcher) {
                    return matcher.group(1)
                }
            }
        }

        throw new RuntimeException("Oops, please add a canonical import path. See https://golang.org/doc/go1.4#canonicalimports'")
    }

    /**
     * Ensures that golang is installed in the given directory. The
     * go binary is then found under $goroot/bin/go.
     */
    static void ensureGoTools(File goroot, String version) {
        // ensure that the directory exists.
        goroot.mkdirs()

        def tarFile = new File(goroot.parentFile, "go-${version}.tar.gz")
        if (!tarFile.exists()) {
            def tarFileTemp = new File("${tarFile}.tmp${System.currentTimeMillis()}")

            // get system, we don't use windows.
            def system = "linux".equalsIgnoreCase(System.getProperty("os.name")) ? "linux" : "darwin";
            def downloadUrl = "https://storage.googleapis.com/golang/go${version}.${system}-amd64.tar.gz".toURL()

            System.out.println("Downloading go $version from $downloadUrl to ${tarFileTemp.toPath()} and moving it to ${tarFile.toPath()}")
            downloadUrl.openStream().withStream { input ->
                Files.copy(input, tarFileTemp.toPath())
                Files.move(tarFileTemp.toPath(), tarFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
        }

        def goBinary = new File(goroot, "bin/go")
        if (!goBinary.exists()) {
            System.out.println("Unpacking go $version")
            def tar = "tar xf ${tarFile} -C ${goroot} --strip-components=1".execute()
            tar.waitForProcessOutput(System.out, System.err)
            if (tar.waitFor() != 0)
                throw new Exception("Could not unpack go to ${goroot}");
        }
    }


    void apply(Project project) {
        project.configure(project) {
            GoPluginExtension config = project.extensions.create("golang", GoPluginExtension)

            afterEvaluate {
                def baseDir = project.rootProject.projectDir

                // get all non-vendored go files relative to the projects directory
                def sourceFiles = project.fileTree(baseDir) {
                    include config.include
                    exclude config.exclude
                }.collect { relativeTo(it, baseDir) }

                def goSourceFiles = sourceFiles.grep { isGoFile(it) }
                if (goSourceFiles.empty) {
                    throw new RuntimeException("No *.go source files found")
                }

                // we put the gopath under /tmp. We dont want it under project.buildDir as some go-tools
                // detect the files under project.buildDir and think they belong to the project,
                // which is not great.
                def projectHash = MessageDigest.getInstance("MD5")
                        .digest(baseDir.absolutePath.bytes)
                        .encodeHex().toString()

                def gopath = new File("/tmp/gopath-${projectHash}")
                def gorootParentDir = System.properties['user.home'] != null ? System.properties['user.home'] : "/tmp"
                def goroot = new File("$gorootParentDir/.golang_gradle/go/${config.goVersion}")
                def go = "${goroot}/bin/go"

                def defaultEnvironmentVariables = [GOROOT: goroot, GOPATH: gopath, PATH: "$goroot/bin/:${System.getenv('PATH')}}"]

                // canonical import of the (root) project
                def canonicalImport = guessCanonicalImport(baseDir)
                def canonicalImportFile = new File(gopath, "src/" + canonicalImport)

                // list of package names
                def packages = goSourceFiles.collect {
                    def subdir = it.parentFile
                    if (subdir != null) {
                        (canonicalImport + "/" + subdir).replaceAll('/$', "")
                    } else {
                        canonicalImport
                    }
                }.toSet()

                // canonical import of this project. should be the same as the canonical import of the root project
                // plus the relative directory of this project to the root project. From this we calculate the
                // gopath-absolute directory where the package files lives.
                def projectSubPath = relativeTo(project.projectDir, baseDir).path
                def projectCanonicalImport = new File(canonicalImport, projectSubPath)
                def projectCanonicalImportFile = new File(gopath, "src/" + projectCanonicalImport)

                // get git hash using git binary.
                String gitHash = "could not get git hash"
                try {
                    gitHash = "git rev-parse HEAD".execute([], baseDir).text.trim()
                } catch (Exception ignored) {
                }

                project.task('clean', type: Exec) {
                    commandLine "rm", "-rf", "build/", config.binaryName, gopath
                }

                if (!project.parent) {
                    project.task('golang') << {
                        ensureGoTools(goroot, config.goVersion)
                    }

                    project.task('gopath', dependsOn: 'golang') << {
                        gopath.mkdirs()

                        sourceFiles.each { file ->
                            def link = new File(canonicalImportFile, file.path)
                            def target = new File(baseDir, file.path)

                            if (!link.parentFile.exists())
                                link.parentFile.mkdirs()

                            // remove target if it exists
                            if (Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS))
                                Files.delete(link.toPath())

                            // link source file to target
                            Files.createSymbolicLink(link.toPath(), target.toPath())

                            // copy file to the build directory
                            // Files.copy(file.toPath(), target.toPath())
                        }
                    }

                    project.task('clean-gopath', type: Exec) {
                        commandLine "rm", "-rf", gopath
                    }

                    project.task("install-goimports", type: Exec, dependsOn: ":gopath") {
                        commandLine go, "get", "golang.org/x/tools/cmd/goimports"
                        environment defaultEnvironmentVariables
                    }

                    project.task("install-junit-report", type: Exec, dependsOn: ":gopath") {
                        commandLine go, "get", "github.com/jstemmer/go-junit-report"
                        environment defaultEnvironmentVariables
                    }

                    project.task("install-glide", type: Exec, dependsOn: ":gopath") {
                        commandLine go, "get", "github.com/Masterminds/glide"
                        environment defaultEnvironmentVariables
                    }

                    project.task("optimize-imports", type: Exec, dependsOn: ["dependencies", ":install-goimports"]) {
                        commandLine(["${gopath}/bin/goimports", "-l", "-w"] + goSourceFiles)
                        workingDir projectCanonicalImportFile
                        environment defaultEnvironmentVariables
                    }

                    project.task("format", type: Exec, dependsOn: [":gopath", "optimize-imports"]) {
                        commandLine([go, "fmt"] + packages)
                        workingDir projectCanonicalImportFile
                        environment defaultEnvironmentVariables
                    }

                    project.task("run-test", type: Exec, dependsOn: ":dependencies") {
                        commandLine([go, "test", "-v"] + packages)
                        workingDir projectCanonicalImportFile
                        environment defaultEnvironmentVariables

                        ignoreExitValue = true
                        standardOutput = new ForwardingByteArrayOutputStream(target: System.out)

                        ext.output = {
                            return standardOutput.toByteArray()
                        }
                    }

                    project.task("test", type: Exec, dependsOn: [":install-junit-report", ":run-test"]) {
                        commandLine "${gopath}/bin/go-junit-report", "--set-exit-code"
                        workingDir projectCanonicalImportFile
                        environment defaultEnvironmentVariables

                        doFirst {
                            new File(buildDir, "outputs").mkdirs()

                            // send the test output to the process
                            standardInput = new ByteArrayInputStream(project.rootProject.tasks."run-test".output())
                            standardOutput = new FileOutputStream(new File(buildDir, "outputs/junit.xml"))
                        }
                    }
                }

                if (!project.hasProperty("noDeps")) {
                    if (new File(project.projectDir, "glide.yaml").exists()) {
                        project.task("dependencies", type: Exec, dependsOn: ":install-glide") {
                            commandLine "${gopath}/bin/glide", "install", "--force"
                            workingDir projectCanonicalImportFile
                            environment defaultEnvironmentVariables
                        }
                    } else {
                        project.task("dependencies", type: Exec, dependsOn: ":gopath") {
                            commandLine go, "get", "-v"
                            workingDir projectCanonicalImportFile
                            environment defaultEnvironmentVariables
                        }
                    }
                }

                project.task("build", type: Exec, dependsOn: "dependencies") {
                    description "Build artifact. Use -PnoDeps to skip dependency downloads/updates."
                    commandLine go, "build", "-a", "-ldflags",
                            "-X=main.Version=${project.version} -X=main.GitHash=${gitHash} -X=main.BuildDate=${DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now())}",
                            "-o", new File(project.buildDir, config.binaryName)

                    workingDir projectCanonicalImportFile
                    environment defaultEnvironmentVariables
                    environment "CGO_ENABLED", config.cgoEnabled ? 1 : 0
                }

                project.task("buildForLinux", type: Exec, dependsOn: "dependencies") {
                    project.logger?.info("Building with cgo enabled: ${config.cgoEnabled}")
                    commandLine go, "build", "-a", "-ldflags",
                            "-X=main.Version=${project.version} -X=main.GitHash=${gitHash} -X=main.BuildDate='${DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now())}'",
                            "-o", new File(project.buildDir, config.binaryName)

                    workingDir projectCanonicalImportFile
                    environment defaultEnvironmentVariables
                    environment "CGO_ENABLED", config.cgoEnabled ? 1 : 0
                    environment "GOOS", "linux"
                    environment "GOARCH", "amd64"
                }

                project.task("goRun", type: Exec, dependsOn: "build") {
                    commandLine new File(project.buildDir, config.binaryName).absolutePath
                }

                project.task("buildStaticForLinux", dependsOn: "buildForLinux") {
                    doFirst {
                        println("[DEPRECATED] buildStaticForLinux is deprecated as this behaviour is now defined by 'cgoEnabled'.")
                    }
                }
            }
        }
    }

    static class GoPluginExtension {
        String binaryName = "output"
        String goVersion = "1.7"
        boolean cgoEnabled = false
        List<String> include = ["**/*.go", "glide.*", "**/*.c", "**/*.cpp", "**/*.h", "**/*.hpp", "resources/**", "static/**"]
        List<String> exclude = ["**/vendor/**", "build/**"]
    }
}
