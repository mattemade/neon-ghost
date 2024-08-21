package io.itch.mattemade.neonghost.world

import io.itch.mattemade.neonghost.character.enemy.Enemy
import io.itch.mattemade.neonghost.character.rei.Player
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.dynamics.contacts.Contact

class GeneralContactListener(
    private val triggerEnterCallback: (Trigger) -> Unit,
    private val triggerExitCallback: (Trigger) -> Unit
) : ContactListener {
    override fun beginContact(contact: Contact) {
        if (contact.ofCategory(ContactBits.REI_PUNCH)) {
            contact.getUserData<Enemy>()?.let { enemy ->
                contact.getUserData<MutableSet<Enemy>>()?.add(enemy)
            }
        }
        if (contact.ofCategory(ContactBits.ENEMY_PUNCH)) {
            contact.getUserData<Player>()?.let { player ->
                contact.getUserData<MutableSet<Player>>()?.add(player)
            }
        }
        if (contact.ofCategory(ContactBits.TRIGGER)) {
            contact.getUserData<Trigger>()?.let { trigger ->
                triggerEnterCallback(trigger)
            }
        }
    }

    override fun endContact(contact: Contact) {
        if (contact.ofCategory(ContactBits.REI_PUNCH)) {
            contact.getUserData<Enemy>()?.let { enemy ->
                contact.getUserData<MutableSet<Enemy>>()?.remove(enemy)
            }
        }
        if (contact.ofCategory(ContactBits.ENEMY_PUNCH)) {
            contact.getUserData<Player>()?.let { player ->
                contact.getUserData<MutableSet<Player>>()?.remove(player)
            }
        }
        if (contact.ofCategory(ContactBits.TRIGGER)) {
            contact.getUserData<Trigger>()?.let { trigger ->
                triggerExitCallback(trigger)
            }
        }
    }

    override fun postSolve(contact: Contact, impulse: ContactImpulse) {}

    override fun preSolve(contact: Contact, oldManifold: Manifold) {}

    private fun Contact.ofCategory(flag: Int): Boolean =
        m_fixtureA?.filterData?.categoryBits == flag || m_fixtureB?.filterData?.categoryBits == flag

    private inline fun <reified T> Contact.getUserData(): T? =
        (getFixtureA()?.userData as? T) ?: (getFixtureB()?.userData as? T)
}