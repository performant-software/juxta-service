
<div id="side-by-side">
    <span id="embedded" style="display: none">${embedded?string}</span>
    <span id="setId" style="display: none">${setId}</span>
    <#assign witnessCount = witnesses?size>
    <span id="witness-count" style="display: none">${witnessCount}</span>
       
    <div id="left-witness" class="sbs-witness">
        <div class="header">
            <div class="sbs-title" title="${witnessDetails[0].name}">${witnessDetails[0].name}</div>
            <a id="change-left" class="juxta-button sbs-button">Change</a>
            <div style="clear: both;"></div>
        </div>
        <span id="left-witness-id" style="display: none">${witnessDetails[0].id}</span>
        <div id="left-witness-text" class="witness-text">
            <@fileReader src="${witnessDetails[0].file}"/> 
        </div>
    </div>
    
    <div id="scroll-mode">
        <img id="scroll-mode-img" src="${baseUrl}/images/lock.gif"/>
    </div>
    
    <div id="left-gutter-div" class="canvas-div"></div>
    <div id="connections-div" class="canvas-div"></div> 
    <div id="right-gutter-div" class="canvas-div"></div>
    
    <div id="right-witness" class="sbs-witness">
        <div class="header">
            <div class="sbs-title" title="${witnessDetails[1].name}">${witnessDetails[1].name}</div>
            <a id="change-right" class="juxta-button sbs-button">Change</a>
            <div style="clear: both;"></div>
        </div>
        <span id="right-witness-id" style="display: none">${witnessDetails[1].id}</span>
        <div id="right-witness-text" class="witness-text">
            <@fileReader src="${witnessDetails[1].file}"/> 
        </div>
    </div>
    
    <div style="clear: both;"></div>
</div>


<!-- popup for selecting a new witness -->
<div class="witnesses-popup">
    <span id="side" style="display: none"></span>
    <div class="header">Select Witness</div>
    <table id="witness-select" class="witness-select" size="${witnessCount}">
        <#list witnesses as witness> 
            <tr class="wit-sel">
               <td class="wit-sel">
                  <p class="witness-option" id="sel-sbs-wit-${witness.id}">${witness.name}</p>
               </td>
            </tr>
        </#list>
    </table>
    <div class="popup-buttons">
        <a id="sbs-ok-button" class="juxta-button sbs-button">OK</a>
        <a id="sbs-cancel-button" class="juxta-button sbs-button">Cancel</a>
    </div>
</div>

