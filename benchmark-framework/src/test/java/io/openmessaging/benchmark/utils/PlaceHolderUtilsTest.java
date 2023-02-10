/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.openmessaging.benchmark.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaceHolderUtilsTest {

    @Test
    public void testReplacePlaceHoldersWithDefault() {
        // variable are non case sensitive
        System.setProperty("testserviceURL", "http://thevalue");
        String text = "serviceUrl: ${testServiceUrl:-http://test/com}";
        String result = PlaceHolderUtils.readAndApplyPlaceholders(text);
        assertEquals("serviceUrl: http://thevalue", result);

        System.clearProperty("testserviceURL");
        result = PlaceHolderUtils.readAndApplyPlaceholders(text);
        assertEquals("serviceUrl: http://test/com", result);
    }
}
