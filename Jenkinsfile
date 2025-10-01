@Library('libpipelines') _

hose {
    EMAIL = 'rocket'
    DEVTIMEOUT = 45
    RELEASETIMEOUT = 45
    DEPLOYONPRS = true
    UPSTREAM_VERSION = '0.56.4'
    VERSIONING_TYPE = "stratioVersion-3-3"
    GRYPE_TEST = false

    DEV = { config ->
        doPackage(config)
        doSsh(conf: config, sshConf: [files: "deployment/CCT/descriptors/*",
                       credentials: "CREDENTIALS_ROCKET_SSH",
                       remoteFolder: "rocketqa/CCT/discovery/${config.INTERNAL_VERSION}/descriptors",
                       remoteServer: "rocketqa.int.stratio.com", activeDelete: true], onPr: false)
        doSsh(conf: config, sshConf: [files: "deployment/CCT/k8s-descriptors/*",
                       credentials: "CREDENTIALS_ROCKET_SSH",
                       remoteFolder: "rocketqa/CCT/discovery/${config.INTERNAL_VERSION}/k8s-descriptors",
                       remoteServer: "rocketqa.int.stratio.com", activeDelete: true], onPr: false)
        doSsh(conf: config, sshConf: [files: "deployment/CCT/rules/*",
                       credentials: "CREDENTIALS_ROCKET_SSH",
                       remoteFolder: "rocketqa/CCT/discovery/${config.INTERNAL_VERSION}/rules",
                       remoteServer: "rocketqa.int.stratio.com", activeDelete: true], onPr: false)
        doDockers(
            conf : config,
            dockerImages :[
                [
                    image : "discovery",
                    dockerfile : "Dockerfile",
                    conf : config,
                    noCache: true
                ]
            ]
        )
    }

    DOC = { config ->
        doStratioDocsChecks(conf: config)
    }
}
