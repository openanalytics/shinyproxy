/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2017 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics;

import javax.inject.Inject;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import eu.openanalytics.services.DockerService;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = ShinyProxyApplication.class)
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
//		List<Container> shinyContainers = dockerService.getShinyContainers();
//		Assert.assertEquals(1, shinyContainers.size());
	}

}
