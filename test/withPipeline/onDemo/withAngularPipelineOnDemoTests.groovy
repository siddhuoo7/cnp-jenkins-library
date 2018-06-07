package withPipeline.onDemo

import groovy.mock.interceptor.MockFor
import org.junit.Test
import uk.gov.hmcts.contino.AngularBuilder
import uk.gov.hmcts.contino.StaticSiteDeployer
import withPipeline.BaseCnpPipelineTest

class withAngularPipelineOnDemoTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleAngularPipeline.jenkins"

  withAngularPipelineOnDemoTests() {
    super("demo", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {

    def mockBuilder = new MockFor(AngularBuilder)
    mockBuilder.demand.with {
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      sonarScan(1) {}
      smokeTest(1) {} //demo-staging
      smokeTest(1) {} // demo-prod
    }

    def mockDeployer = new MockFor(StaticSiteDeployer)
    mockDeployer.ignore.getServiceUrl() { env, slot -> return null} // we don't care when or how often this is called
    mockDeployer.demand.with {
      // demo-staging
      deploy() {}
      healthCheck() { env, slot -> return null }
      // demo-prod
      healthCheck() { env, slot -> return null }
    }

    mockBuilder.use {
      mockDeployer.use {
        runScript("testResources/$jenkinsFile")
      }
    }
  }
}
