The dependencies of these project will be present in the base image used by the dockerfile from where we call the script, which is the one we generate from stratio-metabase (stratio-metabase-builder). 

We make this little script because we want to reuse the metabase build scripts to build our drivers but we have the problem that building the drivers requieres having the metabase uberjar (I do not know really why...). The metabase build scripts check if we have an up-to-date uberjar with a checksum of the source files. The source files are added in the base image but the checksum of the jar is erased in the last metabase build. Since we know our metabase.jar is the "correct" one we just calculate the checksum from the source and place it where the driver build scrips will look for it.

