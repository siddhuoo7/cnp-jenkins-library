package uk.gov.hmcts.tests

interface JenkinsStepMock {


  Object sh(String)
  Object tool(HashMap)
  HashMap getEnv()
  String libraryResource(String)
  Object withCredentials(ArrayList, Closure)

}



