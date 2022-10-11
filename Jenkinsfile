#!/usr/bin/groovy
@Library(['platform-cicd-pipeline-library@master', 'platform-cicd-blueprint-library@master']) _

import io.genesaas.cicd.StackSharedConfig
import io.genesaas.cicd.Defaults

def clusterId = Defaults.openshiftConfiguration.id
def hbStackRepoSuffix = "-${clusterId}"

properties([
        parameters([
                choice(choices: 'create\nsync', description: 'To create or to sync client and its environment', name: 'OPERATION'),
                string(name: 'CLIENT_ID', defaultValue: '', description: '[OPTIONAL] Client ID of the client to be updated. Only applicable when OPERATION=update.'),
                booleanParam(name: 'DEPLOY_CLIENT_SHARED', defaultValue: true, description: 'Select if you want to sync Client Shared environment. Only applicable when OPREATION=sync.'),
                booleanParam(name: 'DEPLOY_CLIENT_DATA_PROD', defaultValue: true, description: 'Select if you want to create/sync Production Client Data environment.'),
                booleanParam(name: 'DEPLOY_CLIENT_DATA_SANDBOX', defaultValue: true, description: 'Select if you want to create/sync Sandbox Client Data environment.'),
                booleanParam(name: 'DEPLOY_USECASE_PROD', defaultValue: true, description: 'Select if you want to create/sync Production Use Case environment.'),
                booleanParam(name: 'DEPLOY_USECASE_SANDBOX', defaultValue: true, description: 'Select if you want to create/sync Sandbox Use Case environment.'),
                booleanParam(name: 'WAIT_TEST_ENVIRONMENT_PROVISIONED', defaultValue: true, description: 'Select if you want to wait for all pods of test environment to be up and running. Only applicable when OPREATION=sync.'),
                booleanParam(name: 'JOIN_NAMESPACES', defaultValue: true, description: 'Select if you want join namespaces of the test environments based on their configurations in userProperties, i.e. if the configurations contains \'svc.cluster.local\', it will try to join to the namespace.'),
                string(name: 'EXTEND_TEST_ENV_FOR_X_HOURS', defaultValue: '0', description: '[OPTIONAL] To extend expiry of test environment for X number of hours. If left empty, it will remain default expiry configured in Provisioning Service.'),
                string(name: 'STACK_GIT_URL', defaultValue: 'https://code.experian.local/scm/dasa/stack-da-saas-rc-service-test-hb-seg.git', description: '[REQUIRED] Stack repo git URL.'),
          		choice(choices: 'elves\nrebel', description: '[REQUIRED] Team name.', name: 'TEAM_NAME'),
          		string(name: 'CLIENT_SHARED_ASSEMBLY_FILTER', defaultValue: 'client-shared-services-lightweight', description: '[REQUIRED] Client Shared blueprint assembly filter.'),
                string(name: 'CLIENT_DATA_PROD_ASSEMBLY_FILTER', defaultValue: 'client-data-prod', description: '[REQUIRED] Production Client Data blueprint assembly filter.'),
                string(name: 'CLIENT_DATA_SANDBOX_ASSEMBLY_FILTER', defaultValue: 'client-data-sandbox', description: '[REQUIRED] Sandbox Client Data blueprint assembly filter. If left empty, \'casemanagementusecase\' will be used.'),
                string(name: 'USECASE_PROD_ASSEMBLY_FILTER', defaultValue: 'prod-lightweight', description: '[REQUIRED] Production Use Case blueprint assembly filter.'),
                string(name: 'USECASE_SANDBOX_ASSEMBLY_FILTER', defaultValue: 'sandbox-lightweight', description: '[REQUIRED] Sandbox Use Case blueprint assembly filter. If left empty, \'casemanagementusecase\' will be used.'),
                string(name: 'USECASE_ENV_REFERENCE_SUFFIX', defaultValue: '', description: '[OPTIONAL] Use case environment reference suffix. If left empty, \'casemanagementusecase\' will be used.'),
                string(name: 'USECASE_ENV_DISPLAY_NAME_SUFFIX', defaultValue: '', description: '[OPTIONAL] Use case environment display name suffix. If left empty, \'casemanagementusecase\' will be used.'),
                booleanParam(name: 'RUN_CM_TEST', defaultValue: false, description: 'Select if you want to run Case Management Usecase test suite after create/update client'),
                booleanParam(name: 'PROVISION_CLIENT_DATA', defaultValue: true, description: 'Select if you want to provision client data'),
                string(name: 'TEST_TYPE', defaultValue: 'daily', description: '[OPTIONAL] Leave empty if running random testing. \'daily\' = daily casemanagementusecase, \'test-bdd-microservice\' = run test-bdd-microservice test execution.'),
                choice(choices: 'e1-generic\ne1-bdd-microservice', description: 'The E1 project to provision. Test repository: e1-generic uses test-bdd-framework, e1-bdd-microservice uses test-bdd-microservice.', name: 'TEST_PROJECT'),
                string(name: 'RELEASE_NAME', description: '[OPTIONAL] Leave empty if running random testing. Reserved for test-bdd-microservice test execution'),
                choice(choices: 'PROD\nSANDBOX', description: '[REQUIRED TEST FIELD] CMS use case environment type to be tested against. (PROD or SANDBOX)', name: 'TEST_ENV_USECASE_ENVTYPE'),
                string(name: 'TEST_EXECUTORS_COUNT', defaultValue: '1', description: '[REQUIRED TEST FIELD] Number of test executor machine. Default is 1.'),
                string(name: 'CUCUMBER_TESTS_BRANCH', defaultValue: 'master', description: '[REQUIRED TEST FIELD] cucumber test branch to check out in executor machine. Default is master.'),
                string(name: 'CUCUMBER_EXECUTION_TAGS', defaultValue: '@test-id-51585597', description: '[REQUIRED TEST FIELD] Cucumber execution tags to specify the tests to run. e.g. @casemanagementusecase'),
                string(name: 'LOGEYE_BUILD_NAME', defaultValue: 'daily casemanagement-develop', description: '[OPTIONAL TEST FIELD] This will be displayed in logeye Build column. Default value is env.BUILD_TAG'),
				booleanParam(name: 'DELETE_TEST_ENV_AFTER_TEST', defaultValue: true, description: 'Select if you want to delete test environment after test completed. Only applicable when RUN_CM_TEST=true.'),
				booleanParam(name: 'USE_HEADLESS_TEST_PIPELINE', defaultValue: false, description: 'Select if you want to use testpipeline-e1-headless.'),
                booleanParam(name: 'DB_CONFIG_PROPERTIES_UPDATE',defaultValue: false, description: """
                                     [OPTIONAL FIELD] Update database configurations within config properties in the automation framework.
                                     <br>The database details will be retrieve from bps pod based on ENVIRONMENT_TYPE provided above.
                                     <br>Note: Only support EKS environment.
                                   """.stripIndent())
        ])
])

def parameters = [:]

/*
    Test Environment configurations
 */
def stackName = "devHellboy"
def usecaseName = "casemanagementusecase"
def stackProfile = StackSharedConfig."${stackName}"
def cmsVersion = "v2"

def cmsUrl = "https://da-saas-cms-seg-service-test-client-mgmt-service.hb-seg-dev.experianone.io"
def oktaDomain = "http://mock-okta-svc.da-saas-rc-service-test.svc.cluster.local/api/v1"

/*
    Test Environment configurations for v2
 */
parameters.put("deployUsecaseProd", params.get("DEPLOY_USECASE_PROD"))
parameters.put("deployUsecaseSandbox", params.get("DEPLOY_USECASE_SANDBOX"))
parameters.put("environmentReferenceSuffix", params.get("USECASE_ENV_REFERENCE_SUFFIX"))
parameters.put("displayNameSuffix", params.get("USECASE_ENV_DISPLAY_NAME_SUFFIX"))
parameters.put("deployClientShared", params.get("DEPLOY_CLIENT_SHARED"))
parameters.put("deployClientDataProd", params.get("DEPLOY_CLIENT_DATA_PROD"))
parameters.put("deployClientDataSandbox", params.get("DEPLOY_CLIENT_DATA_SANDBOX"))
parameters.put("clientSharedAssemblyFilter", params.get("CLIENT_SHARED_ASSEMBLY_FILTER"))
parameters.put("prodClientDataAssemblyFilter", params.get("CLIENT_DATA_PROD_ASSEMBLY_FILTER"))
parameters.put("sandboxClientDataAssemblyFilter", params.get("CLIENT_DATA_SANDBOX_ASSEMBLY_FILTER"))
parameters.put("prodUsecaseAssemblyFilter", params.get("USECASE_PROD_ASSEMBLY_FILTER"))
parameters.put("sandboxUsecaseAssemblyFilter", params.get("USECASE_SANDBOX_ASSEMBLY_FILTER"))
parameters.put("checkTestEnvStatus", params.get("WAIT_TEST_ENVIRONMENT_PROVISIONED"))
parameters.put("extendTestEnvExpiryInHours", params.get("EXTEND_TEST_ENV_FOR_X_HOURS").toInteger())
parameters.put("joinNamespace", params.get("JOIN_NAMESPACES")) // to join namespace with mock STS
parameters.put("stackGitUrl", params.get("STACK_GIT_URL"))
parameters.put("usecaseHasClientData", params.get("PROVISION_CLIENT_DATA"))
parameters.put("applicationName", usecaseName)
parameters.put("cmsStack", stackName)
parameters.put("operation", params.get("OPERATION"))
parameters.put("clientId", params.get("CLIENT_ID"))
parameters.put("team", params.get("TEAM_NAME"))
parameters.put("deploymentDeleteImmediatelyAfterTests", params.get("DELETE_TEST_ENV_AFTER_TEST"))
parameters.put("clientManagementServiceUrl", cmsUrl)
parameters.put("clientManagementOktaDomain", oktaDomain)

/*
    Configuration specifically for test-bdd-microservice
 */
def releaseName = params.get("RELEASE_NAME")
if (params.get("TEST_TYPE")?.trim().equals("test-bdd-microservice")
        && (releaseName != null && !releaseName.trim().isEmpty())) {
    parameters.put("usecaseProdOperation", "automation")
    parameters.put("prodUsecaseAssemblyGitRawUrl", "https://code.experian.local/projects/dasa/repos/software-use-gsg-casemanagementusecase/raw/blueprint/development/casemanagementusecase-light-prod-override-assembly.yml?at=refs/heads/master")
    parameters.put("prodUsecaseOverridePropertiesYaml", "{CASE_MANAGEMENT_SERVICE_RELEASE: ${releaseName}}")
    parameters.put("prodUsecaseOverridePropertiesFilepath", "pc-casemanagementusecase/properties.yml")
}

/*
    TEST configurations
 */
def runTest = params.get("RUN_CM_TEST") ?: false
//test value null will skip deprovisioned logic
def test = null

if (runTest) {
    // must wait for environment to complete if run test is selected
    parameters.put("checkTestEnvStatus", true)

    test = { profile, provisioningResults, clientId ->

        // test environment
        def usecaseEnvType = params.get("TEST_ENV_USECASE_ENVTYPE")

        // testpipelineBranchName for testpipeline-saas-cucumber-cms
        def testpipelineBranchName = "master"
        def logeyeBuildName = params.get("LOGEYE_BUILD_NAME")?.trim() ?: env.BUILD_TAG        
        def cucumberExecutionTags = params.get("CUCUMBER_EXECUTION_TAGS")?.trim() ?: 'not @skip and not @skip-on-${browser} and @casemanagementusecase'
        def testPlanName = params.get("TESTRAIL_TEST_PLAN_NAME")?.trim() ?: 'Develop - Case Management Use Case (testing)'
        def printMicroserviceRelease = false
        def testExecutorCount = params.get("TEST_EXECUTORS_COUNT")?.trim() ?: "1"
        def testProject = params.get("TEST_PROJECT") ?: 'e1-generic'

        // skip TestRail result population by default to avoid incorrect result being populated in TestRail
        def importTestrailResult = false
      
        def testRailResultVersion = ""
        def testRailResultTemplate = 'e1-userjourney'
        
        // special handling for daily casemanagementusecase execution
        if (params.get("TEST_TYPE")?.trim().equals("daily")) {          
            testpipelineBranchName = "master"
            usecaseEnvType = "PROD"
            testExecutorCount = "1"
          	importTestrailResult = true
            testPlanName = "Daily EKS Automation CM - BPS"
            logeyeBuildName = "Develop"
			cucumberExecutionTags = 'not @skip and not @skip-on-${browser} and @casemanagementusecase'
			println "testRailResultVersion: ${env.BUILD_TAG}"      
            testRailResultVersion = env.BUILD_TAG
            printMicroserviceRelease = true          
        } else if (params.get("TEST_TYPE")?.trim().equals("test-bdd-microservice")) {
          	importTestrailResult = true
            testPlanName = "EKS Automation test-bdd-microservice"
            logeyeBuildName = "Develop"
			cucumberExecutionTags = 'not @skip and not @skip-on-${browser} and @casemanagementusecase'
        }

        def daSaasRcConfig = [
                cmsAclEnabled           : false,
                printMicroserviceRelease: printMicroserviceRelease,
                cmsUsecaseSecretName    : "",
                billingSecretName       : "default-billing-client1",
                webEngineWraFileUrl     : "",
                solutionParameterFileUrl: "",
                clientManagementServiceUrl  : "https://da-saas-cms-seg-service-test-client-mgmt-service.hb-seg-dev.experianone.io",
                clientManagementServiceAllClientsNamespaceProd : "da-saas-rc-service-test"
        ]
        profile = profile + daSaasRcConfig
        importTestrailResult = false

		def pipelineName = "testpipeline-e1/${testpipelineBranchName}"
		def selectedPipeline = params.get("USE_HEADLESS_TEST_PIPELINE") ?: false
		if (selectedPipeline) { pipelineName = "testpipeline-e1-headless/${testpipelineBranchName}" }
		
        build job: "${pipelineName}",
                parameters: [
                        string(name: 'PROVISIONING_SERVICE_URL', value: profile.provisioningServiceUrl),
                        string(name: 'CMS_URL', value: profile.clientManagementServiceUrl),
                        string(name: 'TOKEN_SERVICE_URL', value: profile.tokenServiceUrl),
                        string(name: 'CLIENT_ID', value: clientId),
                        string(name: 'CMS_ENVIRONMENT', value: profile.clientManagementServiceAllClientsNamespaceProd),
                        string(name: 'ENVIRONMENT_TYPE', value: usecaseEnvType),
                        string(name: 'TEST_PROJECT', value: testProject),
                        string(name: 'CUCUMBER_TAGS', value: cucumberExecutionTags),
                        string(name: 'CUCUMBER_TESTS_BRANCH', value: params.get("CUCUMBER_TESTS_BRANCH")?.trim()),
                        string(name: 'TEST_EXECUTORS_COUNT', value: testExecutorCount),
                        string(name: 'LOGEYE_RUN_NAME', value: logeyeBuildName),
                        string(name: 'LOGEYE_RUN_EXTRA_TAGS', value: ""),
                        booleanParam(name: 'IMPORT_TESTRAIL', value: importTestrailResult),                        
                        string(name: 'TEMPLATE_TESTRAIL', value: testRailResultTemplate),
                        string(name: 'TESTRAIL_TEST_PLAN_NAME', value: testPlanName),
                        string(name: 'BROWSER_TYPE', value: "chrome"),
                        booleanParam(name: 'DB_CONFIG_PROPERTIES_UPDATE', value: params.get('DB_CONFIG_PROPERTIES_UPDATE'))
                ]
    }
}
/*
    Jenkins job
 */
def clientId = params.get("CLIENT_ID")
def jobDescription = "${operation} client with CMS ${cmsVersion}:"
currentBuild.description = "${jobDescription}\nId: ${clientId}"

def clientEmailDomain
//deprovision logic done here
def clientModel = provisionClientUseCaseV2(parameters, test, "gsg-gds-elves-team@experian.com")
clientId = clientModel.id
clientEmailDomain = clientModel.emailDomain

currentBuild.description = "${jobDescription}\nId: ${clientId}\nEmailDomain: ${clientEmailDomain}"
