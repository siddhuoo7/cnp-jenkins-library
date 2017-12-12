package uk.gov.hmcts.contino

import groovy.json.StringEscapeUtils
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.bind.DatatypeConverter
import java.security.InvalidKeyException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.cloudbees.groovy.cps.NonCPS

class MetricsPublisher implements Serializable {

  private final static String defaultCosmosDbUrl = 'https://build.documents.azure.com/'
  def steps
  def env
  def currentBuild
  def cosmosDbUrl
  def buildStartTimeMillis

  MetricsPublisher(steps, currentBuild, cosmosDbUrl) {
    this.steps = steps
    this.env = steps.env
    this.currentBuild = currentBuild
    this.cosmosDbUrl = cosmosDbUrl
    this.buildStartTimeMillis = currentBuild?.startTimeInMillis
  }

  MetricsPublisher(steps, currentBuild) {
    this(steps, currentBuild, defaultCosmosDbUrl)
  }

  @NonCPS
  private def collectMetrics() {
    return [
      id: UUID.randomUUID().toString(),
      branch_name: env.BRANCH_NAME
//      change_id: env.CHANGE_ID,
//      change_url: env.CHANGE_URL,
//      change_title: env.CHANGE_TITLE,
//      change_author: env.CHANGE_AUTHOR,
//      change_author_display_name: env.CHANGE_AUTHOR_DISPLAY_NAME,
//      change_author_email: env.CHANGE_AUTHOR_EMAIL,
//      change_target: env.CHANGE_TARGET,
//      build_number: env.BUILD_NUMBER,
//      build_id: env.BUILD_ID,
//      build_display_name: env.BUILD_DISPLAY_NAME,
//      job_name: env.JOB_NAME,
//      job_base_name: env.JOB_BASE_NAME,
//      build_tag: env.BUILD_TAG,
//      node_name: env.NODE_NAME,
//      node_labels: env.NODE_LABELS,
//      workspace: env.WORKSPACE,
//      build_url: env.BUILD_URL,
//      job_url: env.JOB_URL,
//      current_build_number: currentBuild.number,
//      current_build_result: currentBuild.result,
//      current_build_current_result: currentBuild.currentResult,
//      current_build_display_name: currentBuild.displayName,
//      current_build_id: currentBuild.id,
//      current_build_time_in_millis: currentBuild.timeInMillis,
//      current_build_duration: currentBuild.duration,
//      current_build_duration_string: currentBuild.durationString,
//      current_build_previous_build: currentBuild.previousBuild.number,
//      current_build_next_build: currentBuild.nextBuild.number,
//      current_build_absolute_url: currentBuild.absoluteUrl
  ]
  }

  @NonCPS
  private static
  def generateAuthToken(verb, resourceType, resourceLink, formattedDate, tokenType, tokenVersion, tokenKey) {
    def stringToSign = verb.toLowerCase() + "\n" + resourceType.toLowerCase() + "\n" + resourceLink + "\n" + formattedDate.toLowerCase() + "\n" + "" + "\n"
    Console.println('Signed payload: ' + StringEscapeUtils.escapeJava(stringToSign))

    def decodedKey = tokenKey.decodeBase64()
    def hash = hmacSHA256(decodedKey, stringToSign)
    def base64Hash = DatatypeConverter.printBase64Binary(hash)

    def authToken = "type=${tokenType}&ver=${tokenVersion}&sig=${base64Hash}"
    return  URLEncoder.encode(authToken, 'UTF-8')
  }

  static def hmacSHA256(secretKey, data) {
    try {
      Mac mac = Mac.getInstance('HmacSHA256')
      SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, 'HmacSHA256')
      mac.init(secretKeySpec)
      byte[] digest = mac.doFinal(data.getBytes())
      return digest
    } catch (InvalidKeyException e) {
      throw new RuntimeException("Invalid key exception while converting to HMac SHA256")
    }
  }

  @NonCPS
  private def generateCommandString() {
    def tokenType = env.COSMOSDB_TOKEN_TYPE ?: 'master'
    def tokenVersion = env.COSMOSDB_TOKEN_VERSION ?: '1.0'
    def tokenKey = env.COSMOSDB_TOKEN_KEY

//    def metrics = collectMetrics()
//    steps.echo collectMetrics()
//    def json = JsonOutput.toJson(metrics)
//
//    def data = json.toString()
    def verb = 'POST'
    def resourceType = "docs"
    def resourceLink = "dbs/tempdb/colls/tempcoll"
    def formattedDate = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.now())

    def authToken = generateAuthToken(verb, resourceType, resourceLink, formattedDate, tokenType, tokenVersion, tokenKey)

    return "curl -i -v -X${verb} -H 'Content-Type: application/json' -H 'Authorization: ${authToken}' -H 'x-ms-version: 2017-02-22' " +
      "-H 'x-ms-date: ${formattedDate}' --max-time 10 --data '{id=${UUID.randomUUID().toString()}, branch_name=chris-test}' '${cosmosDbUrl}${resourceLink}/${resourceType}'"
  }

  def publish() {
    if (env.COSMOSDB_TOKEN_KEY == null) {
      steps.echo "Set the 'COSMOSDB_TOKEN_KEY' environment variable to enable metrics publishing"
      return
    }

    try {
      def commandString = generateCommandString()
      steps.echo commandString
      steps.sh script: "${commandString}", returnStdout: true
    } catch (err) {
      steps.echo "Unable to log metrics '${err.message}'"
    }
  }
}
