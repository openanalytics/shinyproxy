/**
 * Copyright 2016 Open Analytics, Belgium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.openanalytics;

import java.util.List;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import com.spotify.docker.client.messages.Container;

import eu.openanalytics.services.DockerService;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ShinyProxyApplication.class)
@WebAppConfiguration
@ActiveProfiles("demo")
public class ShinyProxyApplicationTests {
	
	@Inject
	public DockerService dockerService;
	
	@Test
	public void contextLoads() {
	}
	
	/**
	 * <code>docker run --rm -p 3838:3838 rocker/shiny</code> should result in
	 * one container found here.
	 */
	@Ignore
	@Test
	public void testGetDockerClient(){
		List<Container> shinyContainers = dockerService.getShinyContainers();
		Assert.assertEquals(1, shinyContainers.size());
	}

}
