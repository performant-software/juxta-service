
<div id="side-by-side">
    <span id="setId" style="display: none">${setId}</span>
    <#assign witnessCount = witnesses?size>
    <span id="witness-count" style="display: none">${witnessCount}</span>
       
    <div id="left-witness" class="left-witness">
        <div class="header">
            <div class="sbs-title" title="${witnessDetails[0].name}">${witnessDetails[0].name}</div>
            <a id="change-left" class="juxta-button sbs-button" href="javascript:changeWitness('change-left');">Change</a>
            <div style="clear: both;"></div>
        </div>
        <span id="left-witness-id" style="display: none">${witnessDetails[0].id}</span>
        <div id="left-witness-text" class="witness-text">
            <@fileReader src="${witnessDetails[0].file}"/> 
        </div>
    </div>
    
    <img id="scroll-mode-img" class="scroll-mode-img" src="${baseUrl}/images/lock.gif"/>
    <div id="left-gutter-div" class="canvas-div"></div>
    <div id="connections-div" class="canvas-div"></div> 
    <div id="right-gutter-div" class="canvas-div"></div>
    
    <div id="right-witness" class="right-witness">
        <div class="header">
            <div class="sbs-title" title="${witnessDetails[1].name}">${witnessDetails[1].name}</div>
            <a id="change-right" class="juxta-button sbs-button" href="javascript:changeWitness('change-right');">Change</a>
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
    <select id="witness-select" class="witness-select" size="${witnessCount}">
        <#list witnesses as witness> 
            <option id="${witness.id}" class="witness-option">${witness.name}</option>
        </#list>
    </select>
    <div class="popup-buttons">
        <a id="sbs-ok-button" class="juxta-button sbs-button" href="javascript:okWitnessChange();">OK</a>
        <a id="sbs-cancel-button" class="juxta-button sbs-button" href="javascript:cancelWitnessChange();">Cancel</a>
    </div>
</div>

