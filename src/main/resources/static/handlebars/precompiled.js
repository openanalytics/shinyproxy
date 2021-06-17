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
!function(){var n=Handlebars.template;(Handlebars.templates=Handlebars.templates||{}).switch_instances=n({1:function(n,l,e,t,a){var o=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return null!=(a=o(e,"if").call(null!=l?l:n.nullContext||{},null!=l?o(l,"active"):l,{name:"if",hash:{},fn:n.program(2,a,0),inverse:n.program(4,a,0),data:a,loc:{start:{line:3,column:8},end:{line:7,column:15}}}))?a:""},2:function(n,l,e,t,a){var o=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return"            <li><b>"+n.escapeExpression("function"==typeof(o=null!=(o=o(e,"name")||(null!=l?o(l,"name"):l))?o:n.hooks.helperMissing)?o.call(null!=l?l:n.nullContext||{},{name:"name",hash:{},data:a,loc:{start:{line:4,column:19},end:{line:4,column:29}}}):o)+"</b></li>\n"},4:function(n,l,e,t,a){var o=null!=l?l:n.nullContext||{},r=n.hooks.helperMissing,u="function",c=n.escapeExpression,i=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return'            <li><a target="_blank" href="'+c(typeof(n=null!=(n=i(e,"url")||(null!=l?i(l,"url"):l))?n:r)==u?n.call(o,{name:"url",hash:{},data:a,loc:{start:{line:6,column:41},end:{line:6,column:50}}}):n)+'">'+c(typeof(n=null!=(n=i(e,"name")||(null!=l?i(l,"name"):l))?n:r)==u?n.call(o,{name:"name",hash:{},data:a,loc:{start:{line:6,column:52},end:{line:6,column:62}}}):n)+"</a></li>\n"},compiler:[8,">= 4.3.0"],main:function(n,l,e,t,a){var o=n.lookupProperty||function(n,l){if(Object.prototype.hasOwnProperty.call(n,l))return n[l]};return"<ul>\n"+(null!=(a=o(e,"each").call(null!=l?l:n.nullContext||{},null!=l?o(l,"instances"):l,{name:"each",hash:{},fn:n.program(1,a,0),inverse:n.noop,data:a,loc:{start:{line:2,column:4},end:{line:8,column:13}}}))?a:"")+"</ul>\n"},useData:!0})}();