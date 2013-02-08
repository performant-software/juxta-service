
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
              <div class="box-title-bar">
                  <span id="mb-wit-id-${i}" style="display:none"></span>
                  <p class="box-title" id="box-title-${i}"></p>
                  <#if !condensed>
                     <p class="hm-anno" id="add-anno-${i}" title="Add annotation"/>
                     <p class="hm-del-anno" id="del-anno-${i}" title="Delete annotation"/>
                  </#if>
                  <div style="clear:both"></div>
              </div>
              <p class="box-content" id="box-txt-${i}"/>
              <p class="box-annotation" id="box-anno-${i}"/>
          </div>
      </#list>
 </div>
