@Library('libpipelines@master') _

hose {
    EMAIL = 'rocket'
    DEVTIMEOUT = 45
    RELEASETIMEOUT = 45
    NEW_VERSIONING = 'true'
    BUILDTOOLVERSION = '3.6.2'
    DEPLOYONPRS = true
    ANCHORE_TEST = true
    ANCHORE_POLICY = 'discovery'
    UPSTREAM_VERSION = '0.38.1'
    FREESTYLE_BRANCHING = true

    DEV = { config ->
        doPackage(config)
        doDeploy(config)
        doDockers(
            conf : config,
            dockerImages :[
                [
                    image : "discovery",
                    dockerfile : "Dockerfile",
                    conf : config
                ]
            ]
        )
    }
}
