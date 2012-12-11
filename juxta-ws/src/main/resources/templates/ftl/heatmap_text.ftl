
<span id="witness-change-indexes" style="display: none">${changeIndexes}</span>
 
 <div id="heatmap-text" class="heatmap-text<#if condensed> condensed</#if>">
      <@fileReader src="${srcFile}"/>    
 </div>
 
 <#if !condensed>
     <div class="note-boxes" id="note-boxes">
          <#list notes as note>
              <div class="note" id="note-${note.id}">
                  <p class="box-content">${note.content}</p>
              </div>
          </#list>
     </div>
</#if>     
 
 <div class="margin-boxes<#if condensed> condensed</#if>" id="margin-boxes">
     <#assign x=numWitnesses>
     <#list 1..x as i>     
          <div class="margin-box<#if condensed> condensed</#if>" id="box-${i}">
              <p class="box-title" id="box-title-${i}"/>
              <p class="box-content" id="box-txt-${i}"/>
          </div>
      </#list>
 </div>
