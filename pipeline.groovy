stage 'Integration'
node {
    checkout scm

    // You can use the same for adding prod later on...
    git branch: 'staging', 
    url: 'https://github.com/nyueserv/sbb-monitoring-dashboard'

    // restore dependecies
    sh 'npm install'

    // this will speed up execution - we will have everything ready...
    stash name: 'everything', 
          excludes: 'test-results/**', 
          includes: '**'
    
    // first we do quick and dirty run (to fail fast if smth is wrong)...
    sh 'npm run test-single-run -- --browsers PhantomJS'
    
    // karma already configured to produce junit-style files...
    step([$class: 'JUnitResultArchiver', 
          testResults: 'test-results/**/test-results.xml'])         
}

// we are all set up to run full-blown tests...
stage 'Browser Testing'

parallel chrome: {
    runTests("Chrome")
}, firefox: {
    runTests("Firefox")
}, safari: {
    runTests("Safari")
}

def runTests(browser) {
    node {

        // clean up before each run, otherwise you can find yourself "one stash behind"...
	sh 'rm -rf *'

        unstash 'everything'

        sh "npm run test-single-run -- --browsers ${browser}"

        // you already have test reports for Java set up, we gonna reuse those...
	step([$class: 'JUnitResultArchiver', 
              testResults: 'test-results/**/test-results.xml'])
    }
}

node {
    notify("[ SUCCESS ] Tests OK, deploy to staging?")
}

input 'Deploy to staging?'

stage name: 'Deploy to staging', concurrency: 1 // It means only one build, latest one, get deployed...
node {
    // You asked whats the easiest way to point learn the build number from the app. Here is it :)
    // we will add build number as the page header for that...
    sh "echo '<h1>${env.BUILD_DISPLAY_NAME}</h1>' >> app/index.html"
    
    // Your compose file is has a volume mapped to this build, 
    sh 'docker-compose up -d --build'
    
    notify '[ SUCCESS ] Dashboard ${env.BUILD_DISPLAY_NAME} is up and running at staging'
}
