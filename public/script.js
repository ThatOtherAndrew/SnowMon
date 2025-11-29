'use strict';

const myTickets = [];

async function purchaseTickets(event) {
    event.preventDefault();
    const button = document.querySelector('.join-queue');
    button.disabled = true; // disable button to prevent duplicate purchases

    const ticketCount = document.getElementById('count').valueAsNumber;
    const response = await fetch('/queue', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({tickets: ticketCount}),
    });

    if (response.status === 201) {
        await watchQueue(response.headers.get('Location'));
    } else if (response.status === 200) {
        alert('Not enough tickets available!');
    } else {
        alert(`Sorry, something went wrong. (HTTP ${response.status})`);
    }

    button.disabled = false;
}

async function watchQueue(location) {
    let position = -1;
    let json;

    while (position !== 0) {
        const response = await fetch(location, {
            headers: {'Accept': 'application/json'},
        });
        json = await response.json();
        position = json["position"];

        let positionText = position;
        if (position === -1) {
            positionText = 'Joining queue...';
        } else if (position === 0) {
            positionText = 'Tickets issued!'
        }
        document.querySelector('.position').innerText = positionText;
    }

    // tickets issued, so show ticket IDs on screen
    myTickets.push(...json["ticketIds"]);
    await updateTickets();
}

async function updateTicketInfo() {
    const response = await fetch('/tickets', {
        headers: {'Accept': 'application/json'},
    });
    const json = await response.json();
    document.querySelector('.artist').innerText = json['artist'];
    document.querySelector('.venue').innerText = json['venue'];
    document.querySelector('.datetime').innerText = new Date(json['datetime']);
    document.querySelector('.count').innerText = json['count'];
}

async function updateTickets() {
    const ul = document.getElementById('ticket-list');
    ul.innerHTML = '';
    for (const ticketId of myTickets) {
        const li = document.createElement('li');
        li.appendChild(document.createTextNode(ticketId));
        ul.appendChild(li);
    }
}

function main() {
    // update every second
    updateTicketInfo().then(() => setInterval(updateTicketInfo, 1000));

    // get ticket form workin'
    document.getElementById('purchase-form').addEventListener('submit', purchaseTickets);
}

addEventListener('load', main);
