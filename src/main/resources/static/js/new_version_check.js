/*
 * ShinyProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
document.addEventListener('DOMContentLoaded', function() {
	document.getElementById('new-version-btn').addEventListener("click", function() {
		 $.get("api/proxy", function(data, status){
		 	if (data.length > 0) {
		 		if (confirm("Warning: you have " + data.length + " apps running, your existing session(s) will be closed once you switch to the new version.")) {
		 			update();
				} 
		 	} else {
		 		update();
		 	}
		 });
		
	});

	var spInstanceCookie = Cookies.get('sp-instance');
	var spLatestInstanceCookie = Cookies.get('sp-latest-instance');

	if (typeof spInstanceCookie !== 'undefined' && typeof spLatestInstanceCookie !== 'undefined') {
		if (spInstanceCookie !== spLatestInstanceCookie) {
			document.getElementById('new-version-banner').style.display = "block";
		}
	}
	
	function update() {
		var path = location.pathname;
		
		Cookies.set('sp-instance', Cookies.get('sp-latest-instance'),  {path: path});
		location.reload();
	}

}, false);