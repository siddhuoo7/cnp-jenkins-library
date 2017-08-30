package uk.gov.hmcts.contino

import groovy.json.JsonSlurperClassic


class Terraform implements Serializable {

  def steps
  def product
/***
 *
 * @param steps Jenkins steps
 * @param product product stack to run
 */
  Terraform(steps, product) {

    this.steps = steps
    this.product = product
  }

/***
 * Run a Terraform init and plan
 * @param env Environment to run plan against
 * @return
 */
  def plan(env) {
    init(env)
    runTerraformWithCreds("get -update=true")

    return runTerraformWithCreds(configureArgs(env, "plan -var 'env=${env}' -var 'name=${product}'"))

  }



  /***
   * Run a Terraform apply, based on a previous apply
   * @param env Environment to run apply against
   * @return
   */
  def apply(env) {
    def isPresetEnvironment = env in ['dev', 'prod', 'test']
    if ((isPresetEnvironment && steps.env.BRANCH_NAME == 'master') ||
        (!isPresetEnvironment && steps.env.BRANCH_NAME != 'master'))
    {
        return runTerraformWithCreds(configureArgs(env,"apply -var 'env=${env}' -var 'name=${product}'"))
    }
  }

  private def init(env) {

    def stateStoreConfig = getStateStoreConfig(env)

    return runTerraformWithCreds("init -reconfigure -backend-config " +
      "\"storage_account_name=${stateStoreConfig.storageAccount}\" " +
      "-backend-config \"container_name=${stateStoreConfig.container}\" " +
      "-backend-config \"resource_group_name=${stateStoreConfig.resourceGroup}\" " +
      "-backend-config \"key=${this.product}/${env}/terraform.tfstate\"")


  }

  private def configureArgs(env, args) {
    if (steps.fileExists("${env}.tfvars")) {
      args = "${args} var-file=${env}.tfvars"
    }
    return args
  }

  private def getStateStoreConfig(env) {

    def stateStores = new JsonSlurperClassic().parseText(steps.libraryResource('uk/gov/hmcts/contino/state-storage.json'))

    def stateStoreConfig = stateStores.find { s -> s.env == env }

    if (stateStoreConfig == null) {
      throw new Exception("State storage for ${env} not found. Is it configured?")
    }

    return stateStoreConfig
  }

  private runTerraformWithCreds(args) {

    setupTerraform()

    return steps.ansiColor('xterm') {
      steps.withCredentials([
        [$class: 'StringBinding', credentialsId: 'sp_password', variable: 'ARM_CLIENT_SECRET'],
        [$class: 'StringBinding', credentialsId: 'tenant_id', variable: 'ARM_TENANT_ID'],
        [$class: 'StringBinding', credentialsId: 'contino_github', variable: 'TOKEN'],
        [$class: 'StringBinding', credentialsId: 'subscription_id', variable: 'ARM_SUBSCRIPTION_ID'],
        [$class: 'StringBinding', credentialsId: 'object_id', variable: 'ARM_CLIENT_ID']]) {


        steps.sh("terraform ${args}")
      }
    }

  }

  private setupTerraform() {
    def tfHome = steps.tool name: 'Terraform', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'

    steps.env.PATH = "${tfHome}:${this.steps.env.PATH}"

  }
}
