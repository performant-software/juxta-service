 
 <div id="heatmap-text" class="heatmap-text">
      <@fileReader src="${srcFile}"/>    
 </div>
 
 <div class="note-boxes" id="note-boxes">
      <#list notes as note>
          <div class="note" id="note-${note.id}">
              <p class="box-content">${note.content}</p>
          </div>
      </#list>
 </div>
 
 <div class="margin-boxes" id="margin-boxes">
     <#assign x=numWitnesses>
     <#list 1..x as i>     
          <div class="margin-box" id="box-${i}">
              <p class="box-title" id="box-title-${i}"/>
              <p class="box-content" id="box-txt-${i}"/>
          </div>
      </#list>
 </div>
