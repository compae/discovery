@Library('libpipelines@master') _

hose {
    EMAIL = 'rocket'
    DEVTIMEOUT = 45
    RELEASETIMEOUT = 45
    NEW_VERSIONING = 'true'

    DEV = { config ->
        doPackage(config)
        doDeploy(config)
    }
}