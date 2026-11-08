/**
 * La planification des analyses : « chaque jour a 3 h, analyse la derniere simulation terminee ».
 *
 * <p>Echo tient le CALENDRIER, il ne declenche rien. Il ouvre une echeance ; le script exterieur
 * vient la lire par {@code GET /api/analyses/attendues}, retire l'export et pousse son rapport. Le
 * script reste maitre de son execution.
 *
 * <p>Pas de Quartz : un {@code @Scheduled} a la minute suffit, et il est idempotent.
 */
package ci.cnam.simulator.quality.planification;
