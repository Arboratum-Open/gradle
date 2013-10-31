/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.initialization

import spock.lang.Specification

/**
 * By Szczepan Faber on 7/5/13
 */
class BuildPhaseProgressTest extends Specification {

    def "knows progress"() {
        def progress = new BuildPhaseProgress("Building", 3);

        expect:
        progress.progress() == "Building 33%"
        progress.progress() == "Building 66%"
        progress.progress() == "Building 100%"

        when:
        progress.progress()

        then:
        thrown(IllegalStateException)
    }
}
