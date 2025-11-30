'use strict';

const myTickets = [];
let cancelled = false;

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
        document.querySelector('.request-id').innerText = (await response.json())['id'];
        document.querySelector('button.cancel').disabled = false;
        await watchQueue(response.headers.get('Location'));
    } else if (response.status === 200) {
        alert('Not enough tickets available!');
    } else {
        alert(`Sorry, something went wrong. (HTTP ${response.status})`);
    }

    button.disabled = false;
}

async function cancelTickets() {
    const requestId = document.querySelector('.request-id').innerText.trim();
    const response = await fetch(`/queue/${requestId}`, {method: 'DELETE'});

    if (response.status === 204) {
        // successfully cancelled
        cancelled = true;
    } else if (response.status === 409) {
        // tickets already fulfilled, too late
        alert('Cannot cancel purchase, tickets already issued!');
    } else if (response.status === 404) {
        // invalid ticket id
        alert('Ticket purchase request not found.');
    } else {
        // everything else (not good)
        alert(`Sorry, something went wrong. (HTTP ${response.status})`);
    }
}

async function watchQueue(location) {
    const span = document.querySelector('.position');
    let position = -1;
    let json;

    while (position !== 0) {
        // Exit early on cancellation
        if (cancelled) {
            cancelled = false;
            span.innerText = 'Cancelled';
            break;
        }

        const response = await fetch(location, {
            headers: {'Accept': 'application/json'},
        });
        json = await response.json();
        position = json['position'];

        // Exit early on tickets successfully issued
        if (position === 0) {
            span.innerText = 'Tickets issued!';
            break;
        }

        span.innerText = position === -1 ? 'Joining queue...' : position;

        // 500ms poll delay to not spam too many requests
        await new Promise(r => setTimeout(r, 500));
    }

    // tickets issued, so hide cancel button and show ticket IDs on screen
    document.querySelector('button.cancel').disabled = true;
    for (const ticketId of json['ticketIds']) {
        myTickets.push({
            requestId: json['id'],
            ticketId: ticketId,
        });
    }
    updateTickets();
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

function updateTickets() {
    const ul = document.getElementById('ticket-list');
    ul.innerHTML = '';
    for (const [index, ticket] of myTickets.entries()) {
        const li = document.createElement('li');
        li.innerHTML = `
<div class="ticket">
    <div class="ticket-index">${index + 1}</div>
    <div>
        <p><b>Request ID:</b> ${ticket.requestId}</p>
        <p><b>Ticket ID:</b> ${ticket.ticketId}</p>
    </div>
</div>
        `.trim();
        ul.appendChild(li);
    }
}

function main() {
    // update every second
    updateTicketInfo().then(() => setInterval(updateTicketInfo, 1000));

    // get ticket form workin'
    document.getElementById('purchase-form').addEventListener('submit', purchaseTickets);

    // and the cancel button too
    document.querySelector('button.cancel').addEventListener('click', cancelTickets);
}

addEventListener('load', main);
