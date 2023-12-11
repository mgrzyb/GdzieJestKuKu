/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {onRequest} from "firebase-functions/v2/https";
import {initializeApp} from "firebase-admin/app";
import {credential} from "firebase-admin";
import {getMessaging} from "firebase-admin/messaging";
import {getFirestore} from "firebase-admin/firestore";
import {
    fulfillGeoLocationRequest,
    getGeoLocationRequest,
    getOrCreateGeoLocationRequest,
    markGeoLocationRequestAsReceived,
    markGeoLocationRequestAsSent
} from "./GeoLocationRequest";

initializeApp({
    credential: credential.applicationDefault(),
});

export const requestLocation = onRequest(async (request, response) => {
    const firestore = getFirestore();

    console.log("RequestLocation body: ", JSON.stringify(request.body));
    const data = request.body.data ?? request.body;

    const recipientToken: string = data.recipient;
    const senderToken: string = data.sender;

    const geoLocationReq = await getOrCreateGeoLocationRequest(firestore, senderToken, recipientToken);
    if (geoLocationReq.status === "fulfilled") {
        console.log("GeoLocationRequest has already been fulfilled. Returning lastLocation.");
        response.send({
            data: {
                requestId: geoLocationReq.id,
                location: geoLocationReq.location,
                locationTimestamp: geoLocationReq.locationTimestamp
            }
        });
    } else {
        const message = {
            data: {
                messageType: 'location-request',
                requestId: geoLocationReq.id
            },
            token: recipientToken,
            android: {
                priority: 'high' as const
            }
        };

        var messageId = await getMessaging().send(message);
        await markGeoLocationRequestAsSent(firestore, geoLocationReq.id, messageId);

        response.send({ data: { requestId: geoLocationReq.id } });
    }
});

export const getLocationRequestStatus = onRequest(async (req, res) => {
    console.log("GetLocationRequest body: ", JSON.stringify(req.body));

    const data = req.body.data ?? req.body;
    const requestId = data.requestId;

    const request = await getGeoLocationRequest(getFirestore(), requestId)

    if (request.status === "fulfilled") {
        res.send({ data: { requestId: request.id, status: request.status, locationLat: request.location.lat, locationLon: request.location.lon, locationTimestamp: request.locationTimestamp } });
    }
    else {
        res.send({ data: { requestId: request.id, status: request.status } });
    }
});

export const ackLocationRequest = onRequest(async (req, res) => {
    console.log("AckLocationRequest body: ", JSON.stringify(req.body));

    const data = req.body.data ?? req.body;
    const requestId = data.requestId;

    await markGeoLocationRequestAsReceived(getFirestore(), requestId);

    res.sendStatus(200);
});

export const sendLocation = onRequest(async (request, response) => {
    console.log("SendLocation body: ", JSON.stringify(request.body));
    const data = request.body.data ?? request.body;

    const requestId = data.requestId;
    const locationLat = data.locationLat;
    const locationLon = data.locationLon;

    const geoLocationReq = await fulfillGeoLocationRequest(getFirestore(), requestId, {
        lat: locationLat,
        lon: locationLon
    });

    const message = {
        notification: {
            title: 'Received location',
            body: `Location: lat: ${locationLat}, lon: ${locationLon}`,
        },
        data: {
            requestId: requestId,
            requestRecipient: geoLocationReq.recipientToken,
            locationLat: locationLat.toString(),
            locationLon: locationLon.toString()
        },
        token: geoLocationReq.senderToken,
        android: {
            priority: 'high' as const,
            notification: {
                clickAction: "GDZIE_JEST_KUKU"
            }
        }
    };

    await getMessaging().send(message);
    response.sendStatus(200);
}); 