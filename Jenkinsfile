@Library('libpipelines@master') _

hose {
    EMAIL = 'rocket'
    DEVTIMEOUT = 45
    RELEASETIMEOUT = 45
    NEW_VERSIONING = 'true'
    BUILDTOOLVERSION = '3.6.2'
    DEPLOYONPRS = true
    ANCHORE_TEST = false
    ANCHORE_POLICY = 'discovery'
    FREESTYLE_BRANCHING = true

    DEV = { config ->
        doPackage(config)
        doDeploy(config)
        doDockers(
            conf : config,
            dockerImages :[
                [
                    image : "stratio-metabase",
                    dockerfile : "Dockerfile",
                    conf : config
                ]
            ]
        )
    }
}
