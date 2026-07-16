function togglePreview()
{
    const drawer = document.getElementById("previewDrawer");
    const button = document.getElementById("previewButton");

    drawer.classList.toggle("open");

    if(drawer.classList.contains("open")){button.textContent = ">>";}
    else{button.textContent = "<<";}
}

function openTab(tabId,buttonId){     
    var tabs=document.getElementsByClassName("tabcontent"); 
    for(var i=0;i<tabs.length;i++){  tabs[i].style.display="none";}  
    
    var buttons = document.getElementsByClassName("tabButton"); 
    for(var i=0;i<buttons.length;i++){buttons[i];} 

    document.getElementById(tabId).style.display="block";  
    document.getElementById(buttonId);     
    localStorage.setItem("activeTab",tabId); 
    localStorage.setItem("activeButton",buttonId); 
} 



function openAssignTab(tabId,buttonId)
{
    const targetedTab = document.getElementById(tabId);
    const targetButton = document.getElementById(buttonId);

    if(targetedTab.style.display === "block"){return;}

    const tabs = document.querySelectorAll(".assignTabContent");
    tabs.forEach(t => t.style.display = "none");

    const buttons = document.querySelectorAll(".assignTabButton");
    buttons.forEach(b => {b.dataset.selected = "false";});

    document.getElementById(tabId).style.display = "block";
    document.getElementById(buttonId).dataset.selected = "true";
}

function openPreviewTab(tabId,buttonId)
{
    const targetedTab = document.getElementById(tabId);
    const targetButton = document.getElementById(buttonId);

    if(targetedTab.style.display === "block"){return;}

    const tabs = document.querySelectorAll(".previewTabContent");
    tabs.forEach(t => t.style.display = "none");

    const buttons = document.querySelectorAll(".previewTabButton");
    buttons.forEach(b => {b.dataset.selected = "false";});

    document.getElementById(tabId).style.display = "block";
    document.getElementById(buttonId).dataset.selected = "true";
}

async function assignedPerson(btn){ 
    console.log("assignedPerson",btn);
    if(btn.dataset.disabled === "true" && btn.dataset.selected !== "true"){return;}

    const date = btn.dataset.date;
    const time = btn.dataset.time;
    const person = btn.dataset.person;
    
    const params =  
    "date=" + encodeURIComponent(date) +
    "&time=" + encodeURIComponent(time) +
    "&person=" + encodeURIComponent(person); 

    const res = await fetch("/assign", 
    { 
        method:"POST", 
        headers: 
        { 
        "Content-Type": 
        "application/x-www-form-urlencoded" 
        }, 
        body:params 
    } );

    const data = await res.json();
    updateButton(btn,data);
    refreshDisabled(data.date);
    updatePreviewTable(data);
} 

function refreshDisabled(date)
{
    const buttons = document.querySelectorAll("button[data-date='" + date + "']");

    buttons.forEach(btn =>
    {
        btn.dataset.disabled = "false";
        const btnDate = btn.dataset.date;
        const btnTime = btn.dataset.time;
        const btnPerson = btn.dataset.person;
        const SLOT_LIMIT = Number(document.body.dataset.slotLimit);
        const DAILY_LIMIT = Number(document.body.dataset.dailyLimit);

        let disabled = false;

        const sameTimeButtons = document.querySelectorAll(
            "button[data-date='" + btnDate +
            "'][data-time='" + btnTime +
            "'][data-selected='true']"
        );

        if(sameTimeButtons.length >= SLOT_LIMIT && btn.dataset.selected !== "true")
        {disabled = true;}

        let count = 0;
        const personButtons = document.querySelectorAll(
            "button[data-date='" + btnDate +
            "'][data-person='" + btnPerson +
            "'][data-selected='true']"
        );

        count = personButtons.length;

        if(count >= DAILY_LIMIT  && btn.dataset.selected !== "true")
        {disabled = true;}

        if(btnTime === "追い出し(左)" || btnTime === "追い出し(右)")
        {
            let otherTime;
            if(btnTime === "追い出し(左)"){otherTime = "追い出し(右)";}
            else{otherTime = "追い出し(左)";}

            const other = document.querySelector(
                "button[data-date='" + btnDate +
                "'][data-time='" + otherTime +
                "'][data-person='" + btnPerson +
                "'][data-selected='true']"
            );

            if(other != null){disabled = true;}
        }

        if(disabled)
        {
            btn.dataset.disabled = "true";
        }
        else
        {
            btn.dataset.disabled = "false";
        }
    });
} 

window.onload = function() 
{ 
    const tabId = localStorage.getItem("activeTab"); 
    const buttonId = localStorage.getItem("activeButton"); 
    if(tabId && buttonId && document.getElementById(tabId) && document.getElementById(buttonId)){changeAssignTab(tabId,buttonId);} 

    adjustPreviewDateTabWidth();
} 

document.addEventListener("DOMContentLoaded", () =>
{
    document.querySelectorAll("#assignArea button[data-date][data-time][data-person]").forEach(btn =>
    {
        btn.addEventListener("click",async () =>
        {
            console.log("assignedPerson",btn);
            await assignedPerson(btn);
        });
    });
});

function updateButton(btn,data)
{
    btn.dataset.selected = String(data.selected);
}

function updatePreviewTable(data)
{
    const date = data.date.replace(/[^a-zA-Z0-9]/g,"_");
    const time = data.time.replace(/[^a-zA-Z0-9]/g,"_");

    for(let i=0; i<data.persons.length; i++)
    {
        const cell = document.getElementById("preview_" + date + "_" + time + "_" + i);
        if(cell != null){cell.textContent = data.persons[i];}
    }

    for(let i=data.persons.length; i<100; i++)
    {
        const cell = document.getElementById("preview_" + date + "_" + time + "_" + i);
        if(cell==null){break;}
        cell.textContent = "";
    }
}

function stateUniformTabs()
{
    const el = document.getElementById("uniformTabs");
    return el && el.checked;
}

function changeAssignTab(tabId,buttonId)
{
    openAssignTab(tabId,buttonId);

    if(stateUniformTabs())
    {
        const date = tabId.replace("assignTab_","");
        
        const previewTabId = "previewTab_" + date;
        const previewButtonId = "btn_previewTab_" + date;
        openPreviewTab(previewTabId,previewButtonId);
    }
}

function changePreviewTab(tabId,buttonId)
{
    openPreviewTab(tabId,buttonId);

    if(stateUniformTabs())
    {
        const date = tabId.replace("previewTab_","");
        
        const assignTabId = "assignTab_" + date;
        const assignButtonId = "btn_assignTab_" + date;
        openAssignTab(assignTabId,assignButtonId);
    }
}

function adjustPreviewDateTabWidth()
{
    const table = document.querySelector("#previewPanel table");
    const tabs = document.querySelector(".previewDateTabs");

    if(table == null || tabs == null){return;}
    tabs.style.width = table.offsetWidth + "px";
}