
<#if !embedded>
  <span id="ajax-base-url" style="display: none">${ajaxBaseUrl}</span>
  <span id="view-heatmap-segment" style="display: none">${viewHeatmapSegment}</span>
  <span id="fragment-segment" style="display: none">${fragmentSegment}</span>
  <span id="annotate-segment" style="display: none">${annotateSegment}</span>
  <span id="show-annotation-controls" style="display: none">yes</span>
</#if>

<span id="setId" style="display: none">${setId}</span>
<span id="baseId" style="display: none">${baseId}</span>
<span id="condensed" style="display: none">${condensed?string}</span>
<span id="witness-filter" style="display: none">${witnessFilter}</span>

<#if !condensed>
    <div id="files" class="files">
        <div class="header">
           Witness List<span id="sort-header">Sort by</span>
                      
           <ul class="dropdown" >
               <li>
                  <input type="radio" class="sort-radio sort-by" name="hm-sort-by" 
                     <#if sortBy=="date">checked="checked"</#if> value="date"/>Date added
               </li>
               <li>
                  <input type="radio" class="sort-radio sort-by" name="hm-sort-by" 
                     <#if sortBy=="name">checked="checked"</#if> value="name"/>Name 
               </li>
               <li id="divider-row"><hr/></li>
               <li>
                  <input type="radio" class="sort-radio" name="hm-sort" 
                     <#if sortOrder=="asc">checked="checked"</#if> value="asc"/>Ascending
               </li>
               <li>
                  <input type="radio" class="sort-radio" name="hm-sort" 
                     <#if sortOrder=="desc">checked="checked"</#if> value="desc"/>Descending
               </li>
           </ul>
        </div>
        <div id="files-scroller" style="overflow: hidden">
           <div id="files-content">
              <#list witnesses as witness>
                  <#if witness.id == baseId>
                      <div class="base set-file" title="${witness.name}"  juxta:date="${witness.date}">
                        <div id="toggle-${witness.id}" class="visibility-toggle base-visibility"></div>
                        <div id="witness-${witness.id}" class="witness base">${witness.name}</div>
                        <div id="base-tag">[base]</div>
                      </div>
                  <#else>
                      <div class="set-witnesss set-file" title="${witness.name}" juxta:date="${witness.date}">
                          <div id="toggle-${witness.id}" class="visibility-toggle" title="Hide witness"></div>
                          <div class="wit-click-area">
                             <div id="witness-${witness.id}" class="witness">${witness.name}</div>
                             <table>
                                 <tr>
                                    <td class="chart-icon <#if witness.hasAnnotations>annotated</#if>" 
                                       <#if witness.hasAnnotations>title="Has annotations"</#if> ></td>
                                    <td class="chart-label">Difference from base</td>
                                    <td id="change-index-${witness.id}" class="change-index"></td>
                                 </tr>
                             </table>
                             <div style="clear: both;"></div>
                          </div>
                      </div>
                  </#if>
                  
              </#list>
           </div>
        </div>
    </div>
</#if>

<div class="heatmap-div <#if condensed>condensed</#if>">
<#if condensed>
   <div id="condensed-header">
      <div id="set-title"><p class="set-title" title="${setTitle}">${setTitle}</p></div> 
      <span>Viewing:</span><p class="base-name" title="${baseName}">${baseName}</p>
      <div style="clear:both"></div>
   </div>
<#else>
      <div class="header">${baseName}</div> 
</#if> 
    
    <#if ( !condensed )>
        <div class="heatmap-toolbar">
            <a class="juxta-button" id="annotations-button"  <#if !hasUserAnnotations>style="display:none"</#if>
               title="List user annotations">User Annotations</a>
            <#if hasRevisions>
               <a class="juxta-button" id="revisions-button" title="Toggle revision styling">Revisions</a>
            </#if>
            <#if hasNotes >
               <a class="juxta-button" id="notes-button" title="Toggle notes visibiity">Notes</a>
            </#if>
            <#if hasBreaks>
               <a class="juxta-button" id="pb-button" title="Toggle page break visibility">Page Breaks</a>
            </#if>
            <#if hasLineNumbers>
               <a class="juxta-button" id="line-num-button" title="Toggle line number visibility">Line Numbers</a>
            </#if>
            <#if !embedded>
               <a class="juxta-button" id="refresh-button" title="Refresh heatmap view">Refresh</a>
            </#if>
        </div> 
    </#if>
    
    <div id="heatmap-scroller" class="heatmap-scroller" >
        <@heatmapStreamer/> 
    </div>
    
    <#if condensed>
        <div id="condensed-heatmap-footer" class="footer">
            <a class="juxta-button condensed" id="condensed-list-button">Witness List</a>
            <a class="juxta-button condensed" id="full-size-link" title="View full-sized share in new browser window">View Full Size</a>
            <div style="clear: both"></div>
        </div>
        
        <!-- popup for selecting a new base witness -->
        <div id="pick-base-popup" class="condensed-witnesses-popup">
            <div class="header">Select Base Witness</div>
            <select id="witness-select" class="witness-select" size="${witnessCount}">
                <#list witnesses as witness> 
                    <option id="${witness.id}" class="witness-option">${witness.name}</option>
                </#list>
            </select>
            <div class="popup-buttons" style="text-align: right">
                <a id="base-cancel-button" class="juxta-button" >Cancel</a>
                <a id="base-ok-button" class="juxta-button" >OK</a>
            </div>
        </div>
    </#if>
        
</div>

<!-- overlay for browsing user annotations -->
<div id="annotation-browser" style="display: none">
   <div id="ua-scroller">
   </div>
</div>

<div style="clear: both;"></div>

