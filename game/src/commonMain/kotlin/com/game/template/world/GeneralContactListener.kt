package com.game.template.world

import com.game.template.character.enemy.Enemy
import com.game.template.character.rei.Player
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.dynamics.contacts.Contact

class GeneralContactListener : ContactListener {
    override fun beginContact(contact: Contact) {
        val fixtureA = contact.getFixtureA()
        val fixtureB = contact.getFixtureB()
        if (fixtureA != null && fixtureB != null) {
            if (fixtureA.filterData.categoryBits == ContactBits.REI_PUNCH || fixtureB.filterData.categoryBits == ContactBits.REI_PUNCH) {
                contact.getUserData<Enemy>()?.let { enemy ->
                    contact.getUserData<MutableSet<Enemy>>()?.add(enemy)
                }
            }
        }
    }

    override fun endContact(contact: Contact) {
        val fixtureA = contact.getFixtureA()
        val fixtureB = contact.getFixtureB()
        if (fixtureA != null && fixtureB != null) {
            if (fixtureA.filterData.categoryBits == ContactBits.REI_PUNCH || fixtureB.filterData.categoryBits == ContactBits.REI_PUNCH) {
                contact.getUserData<Enemy>()?.let { enemy ->
                    contact.getUserData<MutableSet<Enemy>>()?.remove(enemy)
                }
            }
        }
    }

    override fun postSolve(contact: Contact, impulse: ContactImpulse) {

    }

    override fun preSolve(contact: Contact, oldManifold: Manifold) {
        //println("contact!")
        val fixtureA = contact.getFixtureA()
        val fixtureB = contact.getFixtureB()
        if (fixtureA != null && fixtureB != null) {
            if (fixtureA.filterData.categoryBits == ContactBits.REI_PUNCH || fixtureB.filterData.categoryBits == ContactBits.REI_PUNCH) {
                // TODO: punch!
                val enemy = contact.getUserData<Enemy>()
                println("punch the $enemy $contact")

            }
        }
    }

    private inline fun <reified T> Contact.getUserData(): T? =
        (getFixtureA()?.userData as? T) ?: (getFixtureB()?.userData as? T)
}