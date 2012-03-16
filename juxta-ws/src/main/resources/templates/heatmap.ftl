
<#if !embedded>
  <span id="ajax-base-url" style="display: none">${ajaxBaseUrl}</span>
  <span id="view-heatmap-segment" style="display: none">${viewHeatmapSegment}</span>
  <span id="fragment-segment" style="display: none">${fragmentSegment}</span>
</#if>

<span id="setId" style="display: none">${setId}</span>
<span id="baseId" style="display: none">${baseId}</span>


<div id="files" class="files">
    <div class="header">Comparison Set</div>
    <#list witnesses as witness>
        <#if witness.id == baseId>
            <div class="base set-file" title="${witness.name}">${witness.name}</div>
        <#else>
            <div class="set-witnesss set-file">
                <div id="witness-${witness.id}" class="witness" title="${witness.name}">${witness.name}</div>
                <div id="change-index-${witness.id}" class="change-index" juxta:change-index="${witness.changeIndex}"></div>
                <div style="clear: both;"></div>
            </div>
        </#if>
    </#list>
</div>

<div class="heatmap-div">
    <div class="header">${baseName}</div>  
    
    <#if (embedded && ( hasRevisions || hasNotes || hasBreaks)) || !embedded >
        <div class="heatmap-toolbar">
            <#if hasRevisions>
                <a class="juxta-button" id="revisions-button" href="javascript:toggleRevisionStyle();" 
                   title="Toggle revision styling">Revisions</a>
            </#if>
            <#if hasNotes>
                <a class="juxta-button" id="notes-button" href="javascript:toggleNotes();"
                   title="Toggle notes visibiity">Notes</a>
            </#if>
            <#if hasBreaks>
                <a class="juxta-button" id="pb-button" href="javascript:togglePbTags();"
                   title="Toggle page break visibility">Page Breaks</a>
            </#if>
            <#if !embedded>
                <a class="juxta-button" id="refresh-button" href="javascript:refreshHeatmap();"
                   title="Refresh heatmap view">Refresh</a>
            </#if>
        </div> 
     </#if>
    
    <div id="heatmap-scroller" class="heatmap-scroller" >
        <@heatmapStreamer/> 
    </div>
        
</div>

<div style="clear: both;"></div>

