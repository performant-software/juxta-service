<span id="witness-change-indexes" style="display: none">${changeIndexes}</span>

<div id="heatmap-text" class="heatmap-text<#if condensed> condensed</#if>">
   <@fileReader src="${srcFile}"/>
</div>

<#if !condensed>
   <div class="note-boxes" id="note-boxes">
      <#list notes as note>
      <div class="note" id="note-${note.id}">
         <p class="box-content">
            ${note.content}
         </p>
      </div>
      </#list>
   </div>
</#if>

<div class="margin-boxes<#if condensed> condensed</#if>" id="margin-boxes">
   <#assign x=numWitnesses>
   <#list 1..x as i>
      <div class="margin-box<#if condensed> condensed</#if>" id="box-${i}">
         <div class="box-title-bar">
            <span id="mb-wit-id-${i}" style="display:none">id</span>
            <p class="box-title" id="box-title-${i}">t</p>
            <#if !condensed>
               <p class="hm-anno add" id="add-anno-${i}" title="Add annotation"/>
            </#if>
            <div style="clear:both"></div>
         </div>
         <p class="box-content" id="box-txt-${i}"/>
         <div class="box-annotation" id="box-annotation-${i}">
            <div class="ua-toolbar">
               <span>Annotation</span>
               <p class="hm-anno edit" id="edit-anno-${i}" title="Edit annotation"/>
               <p class="hm-del-anno" id="del-anno-${i}" title="Delete annotation"/>
               <div style="clear:both"></div>
            </div>
            <p class="box-annotation" id="box-anno-${i}"/>
         </div>
         <div class="box-edit-annotation" id="box-edit-annotation-${i}">
            <div class="ua-toolbar">
               <span>Add Annotation</span>
            </div>
            <textarea id="annotation-editor"></textarea>
            <div class="anno-buttons">
               <a id="anno-ok-button" class="juxta-button sbs-button">OK</a>
               <a id="anno-cancel-button" class="juxta-button sbs-button">Cancel</a>
               <div style="clear:both"></div>
            </div>
         </div>
         <div class="box-del-annotation" id="box-del-annotation-${i}">
            <span>Delete annotation?</span>
            <a id="del-anno-ok-button" class="juxta-button sbs-button">Yes</a>
            <a id="del-anno-cancel-button" class="juxta-button sbs-button">No</a>
            <div style="clear:both"></div>
         </div>
      </div>
   </#list>
</div>