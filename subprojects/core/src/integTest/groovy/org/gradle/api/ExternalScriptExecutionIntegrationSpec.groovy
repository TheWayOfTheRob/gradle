/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpServer

class ExternalScriptExecutionIntegrationSpec extends AbstractIntegrationSpec {
    @org.junit.Rule
    public final HttpServer server = new HttpServer()

    def "uses encoding specified by http server"() {
        given:
        executer.withDefaultCharacterEncoding("UTF-8")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""
task check {
    doLast {
        assert java.nio.charset.Charset.defaultCharset().name() == "UTF-8"
        // embed a euro character in the text - this is encoded differently in ISO-8859-15 and UTF-8
        assert '\u20AC'.charAt(0) == 0x20AC
    }
}
""", "ISO-8859-15")
        assert scriptFile.getText("ISO-8859-15") != scriptFile.getText("UTF-8")
        server.expectGet('/script.gradle', scriptFile).contentType("text/plain; charset=ISO-8859-15")

        and:
        buildFile << "apply from: 'http://localhost:${server.port}/script.gradle'"

        expect:
        succeeds 'check'
    }

    def "assumes utf-8 encoding when none specified by http server"() {
        given:
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""
task check {
    doLast {
        assert java.nio.charset.Charset.defaultCharset().name() == "ISO-8859-15"
        // embed a euro character in the text - this is encoded differently in ISO-8859-15 and UTF-8
        assert '\u20AC'.charAt(0) == 0x20AC
    }
}
""", "UTF-8")
        assert scriptFile.getText("ISO-8859-15") != scriptFile.getText("UTF-8")
        server.expectGet('/script.gradle', scriptFile).contentType("text/plain")

        and:
        buildFile << "apply from: 'http://localhost:${server.port}/script.gradle'"

        expect:
        succeeds 'check'
    }

    def "will cache when offline"() {
        given:
        String scriptName = "script-${System.currentTimeMillis()}.gradle"
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""
task check {
}
""", "UTF-8")
        server.expectGet('/' + scriptName, scriptFile)

        and:
        buildFile << "apply from: 'http://localhost:${server.port}/${scriptName}'"

        expect:
        succeeds 'check'

        and:
        server.stop()

        when:
        scriptFile.setText("""
task check {
throw new GradleException()
}
""", "UTF-8")

        then:
        args("--offline")
        succeeds 'check'
    }
}
