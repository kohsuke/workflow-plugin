def hello(String name) {
    echo "Hello ${name}"
    node {
        sh "sleep 5"
    }
}