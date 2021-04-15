@Library('libpipelines@master') _

hose {
    EMAIL = 'rocket'
    DEVTIMEOUT = 45
    RELEASETIMEOUT = 45
    NEW_VERSIONING = 'true'
    BUILDTOOLVERSION = '3.6.2'
    DEPLOYONPRS = true
    ANCHORE_TEST = false
    PKGMODULESNAMES = ['discovery']
    ANCHORE_POLICY = 'discovery'
    UPSTREAM_VERSION = '0.38.1'
    FREESTYLE_BRANCHING = true
    DOWNLOADS_USER = "rocketqa"
    SSH_CREDENTIALS_SERVER_ID = "SSH_ROCKETQA"

    DEV = { config ->
        doPackage(config)
        doSsh(config, [onPr: true, regex: "deployment/CCT/descriptors/.*",
                       remoteFolder: "rocketqa/CCT/discovery/${config.UPSTREAM_VERSION}-${config.INTERNAL_VERSION}/descriptors",
                       remoteServer: "rocketqa.int.stratio.com", onPr: false, type: "push"], true)
        doSsh(config, [onPr: true, regex: "deployment/CCT/rules/.*",
                       remoteFolder: "rocketqa/CCT/discovery/${config.UPSTREAM_VERSION}-${config.INTERNAL_VERSION}/rules",
                       remoteServer: "rocketqa.int.stratio.com", onPr: false, type: "push"], true)
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
