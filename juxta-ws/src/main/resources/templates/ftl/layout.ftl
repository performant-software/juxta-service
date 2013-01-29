<#if !embedded>
<html>
    <head>
        <meta charset="utf-8"> 
        <title> 
            ${title}
        </title> 
        
        <!-- stylesheets -->
        <link href="${baseUrl}/stylesheets/juxta-ws.css" rel="stylesheet" type="text/css">
        <link href="${baseUrl}/stylesheets/heatmap.css" rel="stylesheet" type="text/css">
        <link href="${baseUrl}/stylesheets/sidebyside.css" rel="stylesheet" type="text/css">
        
        <!-- javascripts -->
        <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js"></script>
        <script type="text/javascript" src="${baseUrl}/javascripts/jquery.mousewheel.min.js"></script>   
        <script type="text/javascript" src="${baseUrl}/javascripts/jquery.tinysort.min.js"></script>   
        <script type="text/javascript" src="${baseUrl}/javascripts/raphael-min.js"></script>    
        <script type="text/javascript" src="${baseUrl}/javascripts/juxta-ws-common.js"></script>
        <script type="text/javascript" src="${baseUrl}/javascripts/juxta-ws.js"></script>
        <script type="text/javascript" src="${baseUrl}/javascripts/heatmap.js"></script> 
        <script type="text/javascript" src="${baseUrl}/javascripts/sidebyside.js"></script>
    </head>
    
    <body>

		<span id="embedded" style="display: none">${embedded?string}</span>
        <span id="curr-workspace" style="display: none">${workspace}</span>
        <span id="curr-workspace-id" style="display: none">${workspaceId}</span>
        <span id="serviceUrl" style="display: none">${baseUrl}</span>
        
        <div class="main">
            <#include "/nav.ftl">
            
            <div class="title-bar">
                <div class="title">
                    ${longTitle!title}
                </div>
                <div class="ws-display">
                    <span>Workspace: ${workspace}&nbsp;&nbsp;</span>
                    <a id="change-workspace" class="juxta-button">Change</a>
                </div>
                <div style="clear: both;"></div>
            </div>
            
            <div id="content-scroller" class="content-scroller">
                <div id="juxta-ws-content" class="juxta-ws-content">
                    <#include "${content}">
                </div>
            </div>
        </div>
        <#include "/footer.ftl">
        
        <!-- Wating overlay -->
        <div id="wait-popup" class="overlay">
            <div class="overlay-body">
                <img src="${baseUrl}/images/loader.gif" />
                <p>Please Wait...</p>
            </div>
        </div>
        
        <!-- Workspace popups -->
        <#include "/workspace_pop.ftl">   
                  
    </body>
</html>

<#else>
    
    <div id="juxta-ws-content" class="juxta-ws-content">
        <#include "${content}">
    </div>     
</#if>


