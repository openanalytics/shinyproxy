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
!function(){var n=Handlebars.template,l=Handlebars.templates=Handlebars.templates||{};l.my_apps=n({1:function(n,l,e,a,t){var o,c=null!=l?l:n.nullContext||{},s=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return"        <li>\n            <b>"+n.escapeExpression("function"==typeof(o=null!=(o=s(e,"displayName")||(null!=l?s(l,"displayName"):l))?o:n.hooks.helperMissing)?o.call(c,{name:"displayName",hash:{},data:t,loc:{start:{line:4,column:15},end:{line:4,column:32}}}):o)+'</b>\n            <ul class="instance-list">\n'+(null!=(o=s(e,"each").call(c,null!=l?s(l,"instances"):l,{name:"each",hash:{},fn:n.program(2,t,0),inverse:n.noop,data:t,loc:{start:{line:6,column:16},end:{line:17,column:25}}}))?o:"")+"            </ul>\n        </li>\n"},2:function(n,l,e,a,t){var o,c=null!=l?l:n.nullContext||{},s=n.hooks.helperMissing,u="function",i=n.escapeExpression,n=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return'                    <li class="app-instance">\n                        <a target="_blank" href="'+i(typeof(o=null!=(o=n(e,"url")||(null!=l?n(l,"url"):l))?o:s)==u?o.call(c,{name:"url",hash:{},data:t,loc:{start:{line:8,column:49},end:{line:8,column:58}}}):o)+'">'+i(typeof(o=null!=(o=n(e,"instanceName")||(null!=l?n(l,"instanceName"):l))?o:s)==u?o.call(c,{name:"instanceName",hash:{},data:t,loc:{start:{line:8,column:60},end:{line:8,column:78}}}):o)+'</a>\n                        <div class="btn-group btn-group-xs" role="group">\n                            <button type="button" class="btn btn-primary"\n                                    onclick="Shiny.instances.eventHandlers.onDeleteInstance(\''+i(typeof(o=null!=(o=n(e,"name")||(null!=l?n(l,"name"):l))?o:s)==u?o.call(c,{name:"name",hash:{},data:t,loc:{start:{line:11,column:93},end:{line:11,column:103}}}):o)+"', '"+i(typeof(o=null!=(o=n(e,"proxyId")||(null!=l?n(l,"proxyId"):l))?o:s)==u?o.call(c,{name:"proxyId",hash:{},data:t,loc:{start:{line:11,column:107},end:{line:11,column:120}}}):o)+"', '"+i(typeof(o=null!=(o=n(e,"spInstance")||(null!=l?n(l,"spInstance"):l))?o:s)==u?o.call(c,{name:"spInstance",hash:{},data:t,loc:{start:{line:11,column:124},end:{line:11,column:140}}}):o)+'\');">\n                                Stop app\n                            </button>\n                        </div>\n                        <span class="uptime">Uptime: '+i(typeof(o=null!=(o=n(e,"uptime")||(null!=l?n(l,"uptime"):l))?o:s)==u?o.call(c,{name:"uptime",hash:{},data:t,loc:{start:{line:15,column:53},end:{line:15,column:65}}}):o)+"</span>\n                    </li>\n"},compiler:[8,">= 4.3.0"],main:function(n,l,e,a,t){var o=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return'<ul class="app-list">\n'+(null!=(e=o(e,"each").call(null!=l?l:n.nullContext||{},null!=l?o(l,"apps"):l,{name:"each",hash:{},fn:n.program(1,t,0),inverse:n.noop,data:t,loc:{start:{line:2,column:4},end:{line:20,column:13}}}))?e:"")+"</ul>\n"},useData:!0}),l.switch_instances=n({1:function(n,l,e,a,t){var o=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return null!=(e=o(e,"if").call(null!=l?l:n.nullContext||{},null!=l?o(l,"active"):l,{name:"if",hash:{},fn:n.program(2,t,0),inverse:n.program(4,t,0),data:t,loc:{start:{line:3,column:8},end:{line:20,column:15}}}))?e:""},2:function(n,l,e,a,t){var o,c=null!=l?l:n.nullContext||{},s=n.hooks.helperMissing,u="function",i=n.escapeExpression,n=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return'            <li class="app-instance">\n                <b>'+i(typeof(o=null!=(o=n(e,"instanceName")||(null!=l?n(l,"instanceName"):l))?o:s)==u?o.call(c,{name:"instanceName",hash:{},data:t,loc:{start:{line:5,column:19},end:{line:5,column:37}}}):o)+'</b>\n                <div class="btn-group btn-group-xs" role="group">\n                    <button type="button" class="btn btn-primary" onclick="Shiny.instances.eventHandlers.onDeleteInstance(\''+i(typeof(o=null!=(o=n(e,"instanceName")||(null!=l?n(l,"instanceName"):l))?o:s)==u?o.call(c,{name:"instanceName",hash:{},data:t,loc:{start:{line:7,column:123},end:{line:7,column:141}}}):o)+"', '"+i(typeof(o=null!=(o=n(e,"proxyId")||(null!=l?n(l,"proxyId"):l))?o:s)==u?o.call(c,{name:"proxyId",hash:{},data:t,loc:{start:{line:7,column:145},end:{line:7,column:158}}}):o)+"', '"+i(typeof(o=null!=(o=n(e,"spInstance")||(null!=l?n(l,"spInstance"):l))?o:s)==u?o.call(c,{name:"spInstance",hash:{},data:t,loc:{start:{line:7,column:162},end:{line:7,column:178}}}):o)+'\');">Stop app</button>\n                    <button type="button" class="btn btn-primary btn-restart-app" onclick="Shiny.instances.eventHandlers.onRestartInstance();">Restart app</button>\n                </div>\n                <span class="uptime">Uptime: '+i(typeof(o=null!=(o=n(e,"uptime")||(null!=l?n(l,"uptime"):l))?o:s)==u?o.call(c,{name:"uptime",hash:{},data:t,loc:{start:{line:10,column:45},end:{line:10,column:57}}}):o)+"</span>\n            </li>\n"},4:function(n,l,e,a,t){var o,c=null!=l?l:n.nullContext||{},s=n.hooks.helperMissing,u="function",i=n.escapeExpression,n=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return'            <li class="app-instance">\n                <a target="_blank" href="'+i(typeof(o=null!=(o=n(e,"url")||(null!=l?n(l,"url"):l))?o:s)==u?o.call(c,{name:"url",hash:{},data:t,loc:{start:{line:14,column:41},end:{line:14,column:50}}}):o)+'">'+i(typeof(o=null!=(o=n(e,"instanceName")||(null!=l?n(l,"instanceName"):l))?o:s)==u?o.call(c,{name:"instanceName",hash:{},data:t,loc:{start:{line:14,column:52},end:{line:14,column:70}}}):o)+'</a>\n                <div class="btn-group btn-group-xs" role="group">\n                    <button type="button" class="btn btn-primary" onclick="Shiny.instances.eventHandlers.onDeleteInstance(\''+i(typeof(o=null!=(o=n(e,"instanceName")||(null!=l?n(l,"instanceName"):l))?o:s)==u?o.call(c,{name:"instanceName",hash:{},data:t,loc:{start:{line:16,column:123},end:{line:16,column:141}}}):o)+"', '"+i(typeof(o=null!=(o=n(e,"proxyId")||(null!=l?n(l,"proxyId"):l))?o:s)==u?o.call(c,{name:"proxyId",hash:{},data:t,loc:{start:{line:16,column:145},end:{line:16,column:158}}}):o)+"', '"+i(typeof(o=null!=(o=n(e,"spInstance")||(null!=l?n(l,"spInstance"):l))?o:s)==u?o.call(c,{name:"spInstance",hash:{},data:t,loc:{start:{line:16,column:162},end:{line:16,column:178}}}):o)+'\');">Stop app</button>\n                </div>\n                <span class="uptime">Uptime: '+i(typeof(o=null!=(o=n(e,"uptime")||(null!=l?n(l,"uptime"):l))?o:s)==u?o.call(c,{name:"uptime",hash:{},data:t,loc:{start:{line:18,column:45},end:{line:18,column:57}}}):o)+"</span>\n            </li>\n"},compiler:[8,">= 4.3.0"],main:function(n,l,e,a,t){var o=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return'<ul class="instance-list">\n'+(null!=(e=o(e,"each").call(null!=l?l:n.nullContext||{},null!=l?o(l,"instances"):l,{name:"each",hash:{},fn:n.program(1,t,0),inverse:n.noop,data:t,loc:{start:{line:2,column:4},end:{line:21,column:13}}}))?e:"")+"</ul>\n"},useData:!0})}();