node('linux') {
  stage ('Poll') {
    checkout([
      $class: 'GitSCM', branches: [[name: '*/main']], extensions: [],
      userRemoteConfigs: [[url: 'https://github.com/zopencommunity/libsodiumport.git']]])
  }
  stage('Build') {
    build job: 'Port-Pipeline', parameters: [
      string(name: 'PORT_GITHUB_REPO', value: 'https://github.com/zopencommunity/libsodiumport.git'),
      string(name: 'PORT_DESCRIPTION', value: 'A modern, portable, easy to use crypto library'),
      string(name: 'BUILD_LINE', value: 'STABLE')
    ]
  }
}
