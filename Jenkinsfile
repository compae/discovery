@Library('libpipelines@master') _

hose {
    EMAIL = 'rocket'
    DEVTIMEOUT = 45
    RELEASETIMEOUT = 45
    NEW_VERSIONING = 'true'
    BUILDTOOLVERSION = '3.6.2'
    DEPLOYONPRS = true

    DEV = { config ->
        doPackage(config)
        doDeploy(config)
    }
}