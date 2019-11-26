package robust.gradle.plugin

import com.meituan.robust.Constants
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

import java.security.MessageDigest
/**
 * Created by hedex on 17/2/14.
 */
class RobustApkHashAction implements Action<Project> {
    @Override
    void execute(Project project) {
        project.android.applicationVariants.each { variant ->
            def packageTask = project.tasks.findByName("package${variant.name.capitalize()}")

            if (packageTask == null) {
                return
            }

            packageTask.doFirst {
//                project.logger.quiet("===start compute robust apk hash===")
//                def startTime = System.currentTimeMillis()
                List<File> partFiles = new ArrayList<>()

                if (isGradlePlugin300orAbove(project)){
                    //protected FileCollection resourceFiles;
                    FileCollection resourceFiles
                    if (isGradlePlugin350orAbove(project)){
                        //gradle 3.5.0 适配
//                        resourceFiles = packageTask.resourceFiles.getAsFileTree()
                    }else if (isGradlePlugin320orAbove(project)) {
                        //gradle 4.6 适配
                        resourceFiles = packageTask.resourceFiles.get()
                    } else {
                        resourceFiles = packageTask.resourceFiles
                    }
                    if (null != resourceFiles) {
                        partFiles.add(resourceFiles.getFiles())
                    }
                    //protected FileCollection dexFolders;
                    FileCollection dexFolders = null
                    try {
                        dexFolders = packageTask.dexFolders
                    } catch (MissingPropertyException e) {
                        // api is not public
                    }
                    if (null != dexFolders) {
                        partFiles.addAll(dexFolders.getFiles())
                    }

                    //protected FileCollection javaResourceFiles;
                    FileCollection javaResourceFiles = null
                    try {
                        javaResourceFiles = packageTask.javaResourceFiles
                    } catch (MissingPropertyException e) {
                        // api is not public
                    }
                    if (null != javaResourceFiles) {
                        partFiles.addAll(javaResourceFiles.getFiles())
                    }

                    //protected FileCollection jniFolders;
                    FileCollection jniFolders = null
                    try {
                        jniFolders = packageTask.jniFolders
                    } catch (MissingPropertyException e) {
                        // api is not public
                    }
                    if (null != jniFolders) {
                        partFiles.addAll(jniFolders.getFiles())
                    }

                    //protected FileCollection assets;
                    FileCollection assets = null;
                    try {
                        if (isGradlePlugin350orAbove(project)){
                            //gradle 3.5.0 适配
                            assets = packageTask.resourceFiles.getAsFileTree()
                        }else if (isGradlePlugin320orAbove(project)) {
                            //gradle 4.6 适配
                            assets = packageTask.assets.get()
                        } else {
                            assets = packageTask.assets
                        }
                    } catch (MissingPropertyException e) {
                    }
                    if (null != assets) {
                        partFiles.add(assets.getFiles())
                    }

                    String robustHash = computeRobustHash(partFiles)

                    if (assets instanceof FileCollection) {
                        FileCollection assetsFileCollection = (FileCollection) assets;
                        createHashFile(assetsFileCollection.asPath, Constants.ROBUST_APK_HASH_FILE_NAME, robustHash)
                    }
                    return

                } else {

                File resourceFile = packageTask.resourceFile
                if (null == resourceFile) {
                    return
                }
                partFiles.add(resourceFile)

                Collection<File> dexFolders = null
                try {
                    dexFolders = packageTask.dexFolders
                } catch (MissingPropertyException e) {
                    // api is not public
                }
                if (null != dexFolders) {
                    partFiles.addAll(dexFolders)
                }

                Collection<File> javaResourceFiles = null
                try {
                    javaResourceFiles = packageTask.javaResourceFiles
                } catch (MissingPropertyException e) {
                    // api is not public
                }
                if (null != javaResourceFiles) {
                    partFiles.addAll(javaResourceFiles)
                }


                Collection<File> jniFolders = null
                try {
                    jniFolders = packageTask.jniFolders
                } catch (MissingPropertyException e) {
                    // api is not public
                }
                if (null != jniFolders) {
                    partFiles.addAll(jniFolders)
                }


                File assets = null;
                try {
                    assets = packageTask.assets
                } catch (MissingPropertyException e) {
                    // Android Gradle Plugin version < 2.2.0-beta1
                }

                if (null != assets) {
                    partFiles.add(assets)
                }

                String robustHash = computeRobustHash(partFiles)

                if (null != assets) {
                    // Android Gradle Plugin is 2.2.0-beta1 + , assets is able to access
                    createHashFile(assets.absolutePath, Constants.ROBUST_APK_HASH_FILE_NAME, robustHash)
                } else {
                    // add robustHashFile to resourceFile
                    File robustHashFile = createHashFile(resourceFile.parentFile.absolutePath, Constants.ROBUST_APK_HASH_FILE_NAME, robustHash)
                    RobustApkHashZipUtils.addApkHashFile2ApFile(resourceFile, robustHashFile);
                }

                String buildRubustDir = "${project.buildDir}" + File.separator + "$Constants.ROBUST_GENERATE_DIRECTORY" + File.separator
                createHashFile(buildRubustDir, Constants.ROBUST_APK_HASH_FILE_NAME, robustHash)

//                def cost = (System.currentTimeMillis() - startTime) / 1000
//                logger.quiet "robust apk hash is $robustHash"
//                logger.quiet "compute robust apk hash cost $cost second"
//                project.logger.quiet("===compute robust apk hash end===")
                }
            }
        }
    }


    def String computeRobustHash(ArrayList<File> partFiles) {
        File sumFile = new File("temp_robust_sum.zip")
        RobustApkHashZipUtils.packZip(sumFile, partFiles)
        String apkHashValue = fileMd5(sumFile)
        if (sumFile.exists()) {
            sumFile.delete()
        }
        return apkHashValue
    }

    def String fileMd5(File file) {
        if (!file.isFile()) {
            return "";
        }
        MessageDigest digest;
        byte[] buffer = new byte[4096];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            FileInputStream inputStream = new FileInputStream(file);
            while ((len = inputStream.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            inputStream.close();
        } catch (Exception e) {
            return "";
        }

        BigInteger bigInt = new BigInteger(1, digest.digest());
        return bigInt.toString(16);
    }

    def static File createHashFile(String dir, String hashFileName, String hashValue) {
        File hashFile = new File(dir, hashFileName)
        if (hashFile.exists()) {
            hashFile.delete()
        }

        FileWriter fileWriter = new FileWriter(hashFile)
        fileWriter.write(hashValue)
        fileWriter.close()
        return hashFile
    }

    static boolean isGradlePlugin300orAbove(Project project) {
        //gradlePlugin3.0 -> gradle 4.1+
        return compare(project.getGradle().gradleVersion, "4.1") >= 0
    }

    static boolean isGradlePlugin320orAbove(Project project) {
        //gradlePlugin3.2.0 -> gradle 4.6+
        //see https://developer.android.com/studio/releases/gradle-plugin
        return compare(project.getGradle().gradleVersion, "4.6") >= 0
    }
    static boolean isGradlePlugin350orAbove(Project project) {
        //gradlePlugin3.5.0 -> gradle 5.4.0+
        //see https://developer.android.com/studio/releases/gradle-plugin
        return compare(project.getGradle().gradleVersion, "5.4.0") >= 0
    }

    /**
     *
     * @param lhsVersion gradle version code {@code lhsVersion}
     * @param rhsVersion second gradle version code {@code rhsVersion} to compare with {@code lhsVersion}
     * @return an integer < 0 if {@code lhsVersion} is less than {@code rhsVersion}, 0 if they are
     *         equal, and > 0 if {@code lhsVersion} is greater than {@code rhsVersion}.
     */
    private static int compare(String lhsVersion, String rhsVersion) {
        def lhsArray = lhsVersion.split("\\.")
        def rhsArray = rhsVersion.split("\\.")
        for (int index = 0; index < rhsArray.size(); index++) {
            if (index < lhsArray.size()) {
                if (lhsArray[index] != rhsArray[index]) {
                    return lhsArray[index].toInteger() > rhsArray[index].toInteger() ? 1 : -1
                }
            } else {
                return -1
            }
        }
        return 0
    }

    static String getGradlePluginVersion() {
        String version = null
        try {
            def clazz = Class.forName("com.android.builder.Version")
            def field = clazz.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
            field.setAccessible(true)
            version = field.get(null)
        } catch (Exception ignore) {
        }
        if (version == null) {
            try {
                def clazz = Class.forName("com.android.builder.model.Version")
                def field = clazz.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
                field.setAccessible(true)
                version = field.get(null)
            } catch (Exception ignore) {
            }
        }
        return version
    }
}